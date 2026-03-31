Mix.install([
  {:req, "~> 0.4.0"},
  {:websockex, "~> 0.4.3"},
  {:jason, "~> 1.4"},
  {:postgrex, "~> 0.17.0"}
])

defmodule UserSession do
  use WebSockex
  def start_link(url, name, parent_pid) do
    WebSockex.start_link(url, __MODULE__, %{name: name, parent_pid: parent_pid, ref: 1})
  end

  def handle_info({:join, topic}, state) do
    msg = [to_string(state.ref), to_string(state.ref), topic, "phx_join", %{}]
    {:reply, {:text, Jason.encode!(msg)}, %{state | ref: state.ref + 1}}
  end

  def handle_info({:push, topic, event, payload}, state) do
    msg = [to_string(state.ref), to_string(state.ref), topic, event, payload]
    {:reply, {:text, Jason.encode!(msg)}, %{state | ref: state.ref + 1}}
  end

  def handle_frame({:text, json}, state) do
    decoded = Jason.decode!(json)
    case decoded do
      [_, _, topic, event, payload] ->
        case event do
          "phx_reply" ->
            if payload["status"] == "ok" do
              send(state.parent_pid, {:joined, state.name, topic})
              if payload["response"] && payload["response"]["status"] == "matched" do
                send(state.parent_pid, {:match_found, state.name, payload["response"]["pair_id"]})
              end
            end
          "matched" ->
            send(state.parent_pid, {:match_found, state.name, payload["pair_id"]})
          "msg" ->
            IO.puts("[SESSION] #{state.name} received msg: #{payload["text"]}")
            send(state.parent_pid, {:chat_received, state.name, topic, payload})
          "peer_left" ->
            send(state.parent_pid, {:peer_left, state.name, topic})
          _ -> :ok
        end
      _ -> :ok
    end
    {:ok, state}
  end

  def handle_disconnect(_status, state) do
    send(state.parent_pid, {:disconnected, state.name})
    {:ok, state}
  end

  def join(pid, topic), do: send(pid, {:join, topic})
  def push(pid, topic, event, payload), do: send(pid, {:push, topic, event, payload})
end

defmodule Runner do
  @auth_api "http://localhost:4000/api"
  @matchmaking_ws "ws://localhost:4004/socket/websocket"
  @signaling_ws   "ws://localhost:4003/socket/websocket"

  def run do
    ts = System.system_time(:second)
    user_names = ["Alice", "Bob", "Charlie", "David", "Eve"]
    users = Enum.map(user_names, fn name ->
      email = "#{String.downcase(name)}_#{ts}@ui.ac.id"
      token = register_and_login(email, "#{String.downcase(name)}#{ts}")
      %{name: name, token: token}
    end)

    parent = self()
    mm_sockets = Enum.reduce(users, %{}, fn u, acc ->
      {:ok, pid} = UserSession.start_link("#{@matchmaking_ws}?token=#{u.token}&vsn=2.0.0", u.name, parent)
      Map.put(acc, u.name, pid)
    end)

    for round <- 1..2 do # Reduce to 2 rounds for speed
      IO.puts("\n--- ROUND #{round} START ---")
      Enum.each(mm_sockets, fn {_, pid} -> UserSession.join(pid, "matchmaking:lobby") end)
      receive_all_joins(5, "matchmaking:lobby")
      matches = wait_for_full_pairs(2, %{})
      IO.puts("Round #{round} Matches: #{inspect(matches)}")

      tasks = Enum.map(matches, fn {pair_id, [u1_name, u2_name]} ->
        u1 = Enum.find(users, &(&1.name == u1_name))
        u2 = Enum.find(users, &(&1.name == u2_name))
        Task.async(fn -> perform_chat_scenario(u1, u2, pair_id) end)
      end)
      Task.await_many(tasks, 60000)
      IO.puts("Round #{round} Complete.")
      Process.sleep(1000)
    end

    IO.puts("\n🎉 STRESS TEST SUCCESSFUL!")
  end

  def perform_chat_scenario(u1, u2, pair_id) do
    IO.puts("[#{u1.name} <-> #{u2.name}] protocol starting...")
    parent = self()
    {:ok, sig1} = UserSession.start_link("#{@signaling_ws}?token=#{u1.token}&vsn=2.0.0", u1.name <> "_Sig", parent)
    {:ok, sig2} = UserSession.start_link("#{@signaling_ws}?token=#{u2.token}&vsn=2.0.0", u2.name <> "_Sig", parent)
    
    chat_topic = "chat:#{pair_id}"
    UserSession.join(sig1, chat_topic)
    UserSession.join(sig2, chat_topic)
    receive_all_joins(2, chat_topic)

    # 3 Bubbles from u1
    for i <- 1..3 do
      txt = "Bubble #{i} from #{u1.name}"
      IO.puts("[#{u1.name}] sending: #{txt}")
      UserSession.push(sig1, chat_topic, "msg", %{"text" => txt})
      wait_for_reply(u2.name <> "_Sig", txt)
      Process.sleep(100)
    end

    # 2 Replies from u2
    for i <- 1..2 do
      txt = "Reply #{i} from #{u2.name}"
      IO.puts("[#{u2.name}] sending: #{txt}")
      UserSession.push(sig2, chat_topic, "msg", %{"text" => txt})
      wait_for_reply(u1.name <> "_Sig", txt)
      Process.sleep(100)
    end

    IO.puts("[#{u1.name} <-> #{u2.name}] Verified. Leaving.")
    sig_topic = "signaling:#{pair_id}"
    UserSession.join(sig1, sig_topic)
    UserSession.join(sig2, sig_topic)
    receive_all_joins(2, sig_topic)
    UserSession.push(sig1, sig_topic, "leave", %{})
    
    Process.sleep(500)
    Process.exit(sig1, :normal)
    Process.exit(sig2, :normal)
  end

  defp wait_for_reply(target, text) do
    receive do
      {:chat_received, ^target, _, %{"text" => ^text}} -> 
        IO.puts("[RUNNER] Verified message for #{target}: #{text}")
        :ok
      {:chat_received, name, _, payload} ->
        IO.puts("[RUNNER] Received OTHER msg for #{name}: #{payload["text"]} (waiting for #{target}: #{text})")
        wait_for_reply(target, text)
      {:disconnected, name} -> raise "#{name} disconnected unexpectedly"
      other -> 
        # IO.puts("[RUNNER] Ignoring: #{inspect(other)}")
        wait_for_reply(target, text)
    after 15000 -> raise "Timeout: #{target} did not receive '#{text}'"
    end
  end

  defp receive_all_joins(0, _), do: :ok
  defp receive_all_joins(n, topic) do
    receive do
      {:joined, _, ^topic} -> receive_all_joins(n - 1, topic)
      {:disconnected, name} -> raise "#{name} disconnected during join on #{topic}"
    after 15000 -> raise "Timeout waiting for join on #{topic}"
    end
  end

  defp wait_for_full_pairs(count, acc) do
    if map_size(acc) >= count and Enum.all?(Map.values(acc), &(length(&1) == 2)) do
      acc
    else
      receive do
        {:match_found, user, pair_id} ->
          list = Map.get(acc, pair_id, [])
          new_list = if user in list, do: list, else: [user | list]
          wait_for_full_pairs(count, Map.put(acc, pair_id, new_list))
      after 30000 -> acc
      end
    end
  end

  defp register_and_login(email, username) do
    Req.post!("#{@auth_api}/register", json: %{
      email: email, password: "password123", username: username,
      name: "Tester", phone: "080000000", university: "UI"
    })
    {:ok, pid} = Postgrex.start_link(database: "temubelajar_auth", username: "postgres", password: "Allenth17")
    Postgrex.query!(pid, "UPDATE users SET verified = true WHERE email=$1", [email])
    GenServer.stop(pid)
    resp = Req.post!("#{@auth_api}/login", json: %{email_or_username: email, password: "password123"})
    resp.body["token"]
  end
end
Runner.run()
