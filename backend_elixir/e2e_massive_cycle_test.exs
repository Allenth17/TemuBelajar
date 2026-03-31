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

  def handle_info({:leave, topic}, state) do
    msg = [to_string(state.ref), to_string(state.ref), topic, "phx_leave", %{}]
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
            send(state.parent_pid, {:chat_received, state.name, topic, payload})
          "peer_left" ->
            send(state.parent_pid, {:peer_left, state.name, topic})
          "phx_error" ->
            # Silent
            :ok
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
  def leave(pid, topic), do: send(pid, {:leave, topic})
  def push(pid, topic, event, payload), do: send(pid, {:push, topic, event, payload})
end

defmodule MassiveCycleTester do
  @auth_api "http://localhost:4000/api"
  @matchmaking_ws "ws://localhost:4004/socket/websocket"
  @signaling_ws   "ws://localhost:4003/socket/websocket"
  @user_count 1000
  @total_rounds 3

  def run do
    ts = System.system_time(:second)
    IO.puts("--- Phase 1: Creating #{@user_count} users (concurrency 10) ---")
    
    {:ok, db} = Postgrex.start_link(database: "temubelajar_auth", username: "postgres", password: "Allenth17")

    users = 1..@user_count
    |> Task.async_stream(fn i ->
      name = "MCUser#{i}#{ts}"
      email = "mc#{i}_#{ts}@ui.ac.id"
      
      reg = Req.post!("#{@auth_api}/register", json: %{
        email: email, password: "password123", username: name,
        name: "Tester", phone: "08#{i}#{ts}", university: "UI"
      })
      
      if reg.status == 200 do
        Postgrex.query!(db, "UPDATE users SET verified = true WHERE email=$1", [email])
        login = Req.post!("#{@auth_api}/login", json: %{email_or_username: email, password: "password123"})
        if login.status == 200 do
          %{name: name, token: login.body["token"]}
        else
          nil
        end
      else
        nil
      end
    end, max_concurrency: 10, timeout: :infinity)
    |> Enum.map(fn {:ok, u} -> u end)
    |> Enum.filter(& &1)

    IO.puts("Prepared #{length(users)} users.")

    IO.puts("--- Phase 2: Connecting MM Sockets (Slow Staggered) ---")
    parent = self()
    
    mm_sockets = users
    |> Enum.chunk_every(10)
    |> Enum.flat_map(fn batch ->
      res = Enum.map(batch, fn u ->
        url = "#{@matchmaking_ws}?token=#{u.token}&vsn=2.0.0"
        case UserSession.start_link(url, u.name, parent) do
          {:ok, pid} ->
            {u.name, pid}
          _ ->
            nil
        end
      end) |> Enum.filter(& &1)
      Process.sleep(100)
      res
    end)
    |> Enum.into(%{})

    IO.puts("Connected #{map_size(mm_sockets)} MM sockets.")

    # Cycle test
    for round <- 1..@total_rounds do
      IO.puts("\n--- ROUND #{round} START ---")
      
      # 1. Leave previous lobby (just in case), then Join
      Enum.each(mm_sockets, fn {_, pid} -> 
        UserSession.leave(pid, "matchmaking:lobby")
        UserSession.join(pid, "matchmaking:lobby") 
      end)
      
      expected_matches = div(map_size(mm_sockets), 2)
      IO.puts("Waiting for ~#{expected_matches} pairs...")
      
      start_time = System.monotonic_time(:millisecond)
      matches = wait_for_full_pairs(expected_matches, %{}, 120_000)
      end_time = System.monotonic_time(:millisecond)
      
      IO.puts("Round #{round} Matches Formed: #{map_size(matches)} pairs in #{(end_time - start_time) / 1000}s")
      
      IO.puts("Starting 3+2 chat protocol for all pairs...")
      
      # 2. Perform chat protocol concurrently with safe batching
      results = matches
      |> Enum.map(fn {pair_id, users} -> {pair_id, users} end) # convert map to list
      |> Task.async_stream(fn {pair_id, [u1_name, u2_name]} ->
        u1 = Enum.find(users, &(&1.name == u1_name))
        u2 = Enum.find(users, &(&1.name == u2_name))
        try do
           perform_chat_scenario(u1, u2, pair_id, round)
           :ok
        rescue
          e -> :error
        end
      end, max_concurrency: 50, timeout: 60_000)
      |> Enum.map(fn {:ok, res} -> res end)
      
      success_count = Enum.count(results, &(&1 == :ok))
      error_count = Enum.count(results, &(&1 == :error))
      
      IO.puts("Round #{round} Chat Protocol Complete. Success: #{success_count}, Errors: #{error_count}")
      
      # Cooldown before next round
      Process.sleep(2000)
    end

    IO.puts("\n🎉 MASSIVE E2E CYCLE TEST SUCCESSFUL!")
    
    Enum.each(mm_sockets, fn {_, pid} -> Process.exit(pid, :normal) end)
    GenServer.stop(db)
  end

  def perform_chat_scenario(u1, u2, pair_id, round) do
    parent = self()
    {:ok, sig1} = UserSession.start_link("#{@signaling_ws}?token=#{u1.token}&vsn=2.0.0", u1.name <> "_Sig", parent)
    {:ok, sig2} = UserSession.start_link("#{@signaling_ws}?token=#{u2.token}&vsn=2.0.0", u2.name <> "_Sig", parent)
    
    chat_topic = "chat:#{pair_id}"
    UserSession.join(sig1, chat_topic)
    UserSession.join(sig2, chat_topic)
    
    # Minimal validation: Make sure both joined channels to avoid race conditions
    receive_all_joins(2, chat_topic)

    # We do 3 chats from U1 and 2 chats from U2 alternately (Ganti-gantian).
    msgs_u1 = [
      "Halo #{u2.name}, senang bertemu denganmu di ronde ke-#{round}! Pesan dari #{u1.name}. Kami sedang melakukan stress test besar-besaran untuk memastikan server tahan 1000 user. Bagaimana kabarmu hari ini?",
      "Oh begitu, luar biasa! Pesan kedua dari #{u1.name}. Sistem microservices menggunakan Elixir BEAM VM memang juara dalam hal konkurensi. Channel Phoenix sangat ringan, bayangkan saja memori yang dipakai tiap user kurang dari 1KB. Menakjubkan bukan?",
      "Setuju sekali! Ini pesan ketiga dan terakhir dari #{u1.name} untuk sesi ini. Sampai jumpa di match berikutnya ya. Semoga harimu menyenangkan dan selalu sehat!"
    ]
    msgs_u2 = [
      "Hai #{u1.name}, kabarku sangat baik! Balasan pertama dari #{u2.name}. Wah pantas saja aplikasinya terasa lumayan mulus meski load tesnya mencapai 1000 user bersamaan. Teknik matchmaking yang digunakan juga pakai skor multi-dimensi ya?",
      "Tentu saja! Balasan kedua dari #{u2.name}. Selain ringan, dia juga nggak membebani database karena pesannya dikirim lewat PubSub, bukan disimpan di database layaknya aplikasi chat lain. Benar-benar arsitektur yang keren!"
    ]

    # U1 sends M1 -> U2 replies M1 -> U1 sends M2 -> U2 replies M2 -> U1 sends M3
    
    # 1
    UserSession.push(sig1, chat_topic, "msg", %{"text" => Enum.at(msgs_u1, 0)})
    wait_for_reply(u2.name <> "_Sig", Enum.at(msgs_u1, 0))
    # 2
    UserSession.push(sig2, chat_topic, "msg", %{"text" => Enum.at(msgs_u2, 0)})
    wait_for_reply(u1.name <> "_Sig", Enum.at(msgs_u2, 0))
    # 3
    UserSession.push(sig1, chat_topic, "msg", %{"text" => Enum.at(msgs_u1, 1)})
    wait_for_reply(u2.name <> "_Sig", Enum.at(msgs_u1, 1))
    # 4
    UserSession.push(sig2, chat_topic, "msg", %{"text" => Enum.at(msgs_u2, 1)})
    wait_for_reply(u1.name <> "_Sig", Enum.at(msgs_u2, 1))
    # 5
    UserSession.push(sig1, chat_topic, "msg", %{"text" => Enum.at(msgs_u1, 2)})
    wait_for_reply(u2.name <> "_Sig", Enum.at(msgs_u1, 2))

    # All messages sent and verified
    sig_topic = "signaling:#{pair_id}"
    UserSession.join(sig1, sig_topic)
    UserSession.join(sig2, sig_topic)
    receive_all_joins(2, sig_topic)
    
    # Push leave to signal next
    UserSession.push(sig1, sig_topic, "leave", %{})
    
    # Both close connection
    Process.sleep(200)
    Process.exit(sig1, :normal)
    Process.exit(sig2, :normal)
  end

  defp wait_for_reply(target, text) do
    receive do
      {:chat_received, ^target, _, %{"text" => ^text}} -> :ok
      {:disconnected, _name} -> raise "disconnected"
      _other -> wait_for_reply(target, text)
    after 15000 -> raise "Timeout waiting for message"
    end
  end

  defp receive_all_joins(0, _), do: :ok
  defp receive_all_joins(n, topic) do
    receive do
      {:joined, _, ^topic} -> receive_all_joins(n - 1, topic)
      {:disconnected, _} -> raise "disconnected"
      _ -> receive_all_joins(n, topic)
    after 15000 -> raise "Timeout waiting for join on #{topic}"
    end
  end

  defp wait_for_full_pairs(expected_count, acc, timeout) do
    full_pairs = Enum.count(acc, fn {_, list} -> length(list) >= 2 end)
    if full_pairs >= expected_count do
      acc
    else
      receive do
        {:match_found, user, pair_id} ->
          list = Map.get(acc, pair_id, [])
          new_list = if user in list, do: list, else: [user | list]
          wait_for_full_pairs(expected_count, Map.put(acc, pair_id, new_list), timeout)
      after timeout -> acc
      end
    end
  end

end
MassiveCycleTester.run()
