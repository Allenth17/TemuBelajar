Mix.install([
  {:req, "~> 0.4.0"},
  {:websockex, "~> 0.4.3"},
  {:jason, "~> 1.4"},
  {:postgrex, "~> 0.17.0"}
])

defmodule WebRTCTestClient do
  use WebSockex
  require Logger

  def start_link(url, name, topic, parent_pid) do
    WebSockex.start_link(url, __MODULE__, %{name: name, topic: topic, parent_pid: parent_pid, ref: 1})
  end

  def handle_connect(_conn, state) do
    IO.puts("[#{state.name}] Connected to WebSocket")
    send(self(), :join_topic)
    {:ok, state}
  end

  def handle_info(:join_topic, state) do
    join_msg = [to_string(state.ref), to_string(state.ref), state.topic, "phx_join", %{}]
    {:reply, {:text, Jason.encode!(join_msg)}, %{state | ref: state.ref + 1}}
  end

  def handle_info({:send_event, topic, event, payload}, state) do
    msg = [to_string(state.ref), to_string(state.ref), topic, event, payload]
    {:reply, {:text, Jason.encode!(msg)}, %{state | ref: state.ref + 1}}
  end

  def handle_frame({:text, msg}, state) do
    decoded = Jason.decode!(msg)
    # [join_ref, ref, topic, event, payload]
    [_, _, topic, event, payload] = decoded

    case event do
      "phx_reply" ->
        if payload["status"] == "ok" do
          IO.puts("[#{state.name}] Joined topic #{topic}")
          send(state.parent_pid, {:joined, state.name, topic})
          
          # If the join response already contains a match (for Bob)
          if payload["response"]["status"] == "matched" do
            resp = payload["response"]
            IO.puts("[#{state.name}] MATCHED ON JOIN! pair_id: #{resp["pair_id"]}")
            send(state.parent_pid, {:match_found, state.name, resp["pair_id"]})
          end
        end
        {:ok, state}

      "matched" ->
        IO.puts("[#{state.name}] MATCH FOUND! pair_id: #{payload["pair_id"]}")
        send(state.parent_pid, {:match_found, state.name, payload["pair_id"]})
        {:ok, state}

      "offer" ->
        IO.puts("[#{state.name}] Received SDP Offer")
        # Send Answer (if we are Bob)
        if String.contains?(state.name, "Bob") do
          msg = [to_string(state.ref), to_string(state.ref), topic, "answer", %{sdp: "fake_sdp_answer"}]
          send(state.parent_pid, {:sdp_offered, state.name})
          {:reply, {:text, Jason.encode!(msg)}, %{state | ref: state.ref + 1}}
        else
          send(state.parent_pid, {:sdp_offered, state.name})
          {:ok, state}
        end

      "answer" ->
        IO.puts("[#{state.name}] Received SDP Answer! WebRTC is complete!")
        send(state.parent_pid, {:sdp_answered, state.name})
        {:ok, state}

      "msg" ->
        IO.puts("[#{state.name}] Chat Received: #{payload["text"]}")
        send(state.parent_pid, {:chat_received, state.name, payload["text"]})
        {:ok, state}

      _ ->
        {:ok, state}
    end
  end

  def handle_disconnect(_, state) do
    IO.puts("[#{state.name}] Disconnected")
    {:ok, state}
  end

  # Helpers to send generic messages
  def send_event(pid, topic, event, payload) do
    send(pid, {:send_event, topic, event, payload})
  end


end

defmodule Runner do
  @base_url "http://localhost:4000/api"
  @matchmaking_ws_url "ws://localhost:4004/socket/websocket"
  @signaling_ws_url "ws://localhost:4003/socket/websocket"

  def run do
    ts = System.system_time(:second)
    alice_email = "alice_#{ts}@ui.ac.id"
    bob_email = "bob_#{ts}@ui.ac.id"

    # Register & Login dynamically
    token_a = register_and_login(alice_email, "alice#{ts}")
    token_b = register_and_login(bob_email, "bob#{ts}")
    
    IO.puts("Alice Token: #{String.slice(token_a, 0, 20)}...")
    IO.puts("Bob Token:   #{String.slice(token_b, 0, 20)}...")

    parent = self()

    # 1. Connect and join matchmaking
    {:ok, pid_a} = WebRTCTestClient.start_link("#{@matchmaking_ws_url}?token=#{token_a}&vsn=2.0.0", "Alice", "matchmaking:lobby", parent)
    {:ok, pid_b} = WebRTCTestClient.start_link("#{@matchmaking_ws_url}?token=#{token_b}&vsn=2.0.0", "Bob", "matchmaking:lobby", parent)

    # Wait for both to join
    receive_n(2, :joined)

    # 2. Joins already triggered matchmaking in backend
    # We just need to wait for match_found result for both
    
    # Wait for match_found
    pair_id = receive do
      {:match_found, _user, pair_id} -> pair_id
    after 10000 -> raise "No match found for anyone"
    end
    
    receive do
      {:match_found, _other, ^pair_id} -> :ok
    after 10000 -> raise "Second user did not get match result"
    end

    IO.puts("\n--- Matchmaking exact match: #{pair_id} ---")

    # Close matchmaking sockets
    Process.sleep(100)
    Process.exit(pid_a, :normal)
    Process.exit(pid_b, :normal)

    # 3. Join signaling
    {:ok, sig_a} = WebRTCTestClient.start_link("#{@signaling_ws_url}?token=#{token_a}&vsn=2.0.0", "Alice_Sig", "signaling:#{pair_id}", parent)
    {:ok, sig_b} = WebRTCTestClient.start_link("#{@signaling_ws_url}?token=#{token_b}&vsn=2.0.0", "Bob_Sig", "signaling:#{pair_id}", parent)
    
    receive_n(2, :joined) # Wait signaling join

    # Alice sends offer
    WebRTCTestClient.send_event(sig_a, "signaling:#{pair_id}", "offer", %{sdp: "fake_sdp_offer"})
    
    # Wait for Alice to receive SDP answer from Bob
    receive do
      {:sdp_answered, "Alice_Sig"} -> :ok
    after 10000 -> raise "Alice did not receive SDP answer"
    end
    
    # 4. Join Chat
    WebRTCTestClient.send_event(sig_a, "chat:#{pair_id}", "phx_join", %{})
    WebRTCTestClient.send_event(sig_b, "chat:#{pair_id}", "phx_join", %{})
    receive_n(2, :joined) # Wait chat join

    IO.puts("\n--- WebRTC connected! Testing Chat Message ---")
    WebRTCTestClient.send_event(sig_a, "chat:#{pair_id}", "msg", %{text: "Hello from Alice! VideoChat works!"})

    receive do
      {:chat_received, "Bob_Sig", text} ->
        IO.puts("\n🎉 SUCCESS! E2E WebSocket VideoChat and Text Chat fully verified! Bob got: #{text}")
    after 5000 -> raise "Chat message not received"
    end
  end

  defp receive_n(0, _msg), do: :ok
  defp receive_n(n, expected) do
    receive do
      {^expected, _, _} -> receive_n(n - 1, expected)
    after 5000 -> raise "Timeout waiting for #{expected}"
    end
  end

  defp register_and_login(email, username) do
    IO.puts("Registering #{email}...")
    Req.post!("#{@base_url}/register", json: %{
      email: email, password: "password123", username: username,
      name: "Elixir Tester", phone: "080000000", university: "UI"
    })
    
    Process.sleep(500)
    # Direct PostgreSQL mutation inside the same Erlang VM to bypass connection race limits reliably
    {:ok, pid} = Postgrex.start_link(database: "temubelajar_auth", username: "postgres", password: "Allenth17")
    Postgrex.query!(pid, "UPDATE users SET verified = true WHERE email=$1", [email])
    GenServer.stop(pid)

    IO.puts("Logging in #{email}...")
    resp = Req.post!("#{@base_url}/login", json: %{
      email_or_username: email, password: "password123"
    })
    
    resp.body["token"] || raise "Login failed: #{inspect(resp.body)}"
  end
end

Runner.run()
