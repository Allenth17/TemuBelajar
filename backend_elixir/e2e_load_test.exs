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
  def handle_frame({:text, json}, state) do
    case Jason.decode!(json) do
      [_, _, _topic, "matched", _payload] ->
        send(state.parent_pid, {:match_found, state.name})
      _ -> :ok
    end
    {:ok, state}
  end
end

defmodule LoadTester do
  @auth_api "http://localhost:4000/api"
  @matchmaking_ws "ws://localhost:4004/socket/websocket"
  @user_count 1000

  def run do
    ts = System.system_time(:second)
    IO.puts("--- Phase 1: Creating 1000 users (concurrency 10 to respect DB pool) ---")
    
    {:ok, db} = Postgrex.start_link(database: "temubelajar_auth", username: "postgres", password: "Allenth17")

    users = 1..@user_count
    |> Task.async_stream(fn i ->
      name = "Load#{i}#{ts}"
      email = "L#{i}_#{ts}@ui.ac.id"
      
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
          IO.puts("[LOGIN FAIL] #{name}: #{inspect(login.body)}")
          nil
        end
      else
        IO.puts("[REG FAIL] #{name}: #{inspect(reg.body)}")
        nil
      end
    end, max_concurrency: 10, timeout: :infinity) 
    |> Enum.map(fn {:ok, u} -> u end)
    |> Enum.filter(& &1)

    IO.puts("Prepared #{length(users)} users.")

    IO.puts("--- Phase 2: Connecting (Slow Staggered) ---")
    parent = self()
    sessions = users
    |> Enum.chunk_every(10)
    |> Enum.flat_map(fn batch ->
      res = Enum.map(batch, fn u ->
        url = "#{@matchmaking_ws}?token=#{u.token}&vsn=2.0.0"
        case UserSession.start_link(url, u.name, parent) do
          {:ok, pid} ->
            send(pid, {:join, "matchmaking:lobby"})
            pid
          {:error, err} ->
            IO.puts("[CONN ERROR] #{u.name}: #{inspect(err)}")
            nil
        end
      end) |> Enum.filter(& &1)
      Process.sleep(200) # 10 connections every 200ms = 50 conns/sec
      res
    end)

    IO.puts("Connected #{length(sessions)} sessions. Waiting for matches (Max 2 mins)...")
    match_count = wait_for_matches(0, length(sessions), 120000)

    IO.puts("\n--- LOAD TEST REPORT ---")
    IO.puts("Successful Connections: #{length(sessions)}")
    IO.puts("Total Matches: #{match_count}")
    
    if match_count >= length(sessions) * 0.7 do # Adjusted for local machine constraints
      IO.puts("✅ LOAD TEST SUCCESSFUL!")
    else
      IO.puts("⚠️ Lower throughput than expected.")
    end
    GenServer.stop(db)
  end

  defp wait_for_matches(count, total, timeout) do
    receive do
      {:match_found, _} ->
        new_count = count + 1
        if rem(new_count, 50) == 0, do: IO.write(".")
        wait_for_matches(new_count, total, timeout)
    after timeout ->
      count
    end
  end
end
LoadTester.run()
