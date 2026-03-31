defmodule AuthService.Realtime.MatchmakingServer do
  @moduledoc """
  GenServer yang mengelola antrian matchmaking.
  Fitur:
  - Auto-remove setelah 60s timeout
  - Optional filter per universitas
  - Heartbeat check setiap 30s
  - Queue position update ke semua client
  - Prefer same-university matching (cross-campus ok jika tidak ada)
  """
  use GenServer
  require Logger

  # 60 detik timeout di antrian
  @queue_timeout_ms 60_000
  # 30 detik interval heartbeat check
  @heartbeat_interval_ms 30_000

  defstruct queue: [], pairs: %{}

  # ─── Client API ─────────────────────────────────────────────────────────────

  def start_link(_opts) do
    GenServer.start_link(__MODULE__, %__MODULE__{}, name: __MODULE__)
  end

  @doc "Masukkan user ke antrian. Mengembalikan {:queued, position} atau {:matched, pair_id, peer_email}"
  def join_queue(email, university \\ nil) do
    GenServer.call(__MODULE__, {:join_queue, email, university})
  end

  @doc "Hapus user dari antrian"
  def leave_queue(email) do
    GenServer.cast(__MODULE__, {:leave_queue, email})
  end

  @doc "Tandai pair sebagai selesai (salah satu skip/disconnect)"
  def end_pair(pair_id) do
    GenServer.cast(__MODULE__, {:end_pair, pair_id})
  end

  @doc "Ambil jumlah user sedang mencari"
  def queue_size do
    GenServer.call(__MODULE__, :queue_size)
  end

  # ─── Server Callbacks ────────────────────────────────────────────────────────

  @impl true
  def init(state) do
    Process.send_after(self(), :heartbeat, @heartbeat_interval_ms)
    {:ok, state}
  end

  @impl true
  def handle_call({:join_queue, email, university}, _from, state) do
    # Kalau sudah ada di antrian, kembalikan posisi saat ini
    already_queued = Enum.any?(state.queue, fn {e, _, _} -> e == email end)

    if already_queued do
      position = find_position(state.queue, email)
      {:reply, {:queued, position}, state}
    else
      case find_best_match(state.queue, email, university) do
        nil ->
          # Tidak ada pasangan, masukkan ke antrian
          timestamp = System.monotonic_time(:millisecond)
          new_queue = state.queue ++ [{email, university, timestamp}]
          position  = length(new_queue)
          broadcast_queue_size(length(new_queue))
          {:reply, {:queued, position}, %{state | queue: new_queue}}

        {peer_email, _peer_university, _ts} ->
          # Ada pasangan! Buat pair
          pair_id   = Ecto.UUID.generate()
          new_queue = Enum.reject(state.queue, fn {e, _, _} -> e == peer_email end)
          new_pairs = Map.put(state.pairs, pair_id, {email, peer_email})
          broadcast_queue_size(length(new_queue))
          {:reply, {:matched, pair_id, peer_email}, %{state | queue: new_queue, pairs: new_pairs}}
      end
    end
  end

  @impl true
  def handle_call(:queue_size, _from, state) do
    {:reply, length(state.queue), state}
  end

  @impl true
  def handle_cast({:leave_queue, email}, state) do
    new_queue = Enum.reject(state.queue, fn {e, _, _} -> e == email end)
    broadcast_queue_size(length(new_queue))
    {:noreply, %{state | queue: new_queue}}
  end

  @impl true
  def handle_cast({:end_pair, pair_id}, state) do
    new_pairs = Map.delete(state.pairs, pair_id)
    {:noreply, %{state | pairs: new_pairs}}
  end

  @impl true
  def handle_info(:heartbeat, state) do
    now = System.monotonic_time(:millisecond)

    {alive, expired} = Enum.split_with(state.queue, fn {_, _, ts} ->
      now - ts < @queue_timeout_ms
    end)

    Enum.each(expired, fn {email, _, _} ->
      Logger.info("Queue timeout for #{email}")
      Phoenix.PubSub.broadcast(
        AuthService.PubSub,
        "matchmaking:#{email}",
        {:queue_timeout, email}
      )
    end)

    if length(expired) > 0 do
      broadcast_queue_size(length(alive))
    else
      # Tetap broadcast stats reguler
      broadcast_queue_size(length(alive))
    end

    Process.send_after(self(), :heartbeat, @heartbeat_interval_ms)
    {:noreply, %{state | queue: alive}}
  end

  # ─── Private Helpers ─────────────────────────────────────────────────────────

  # Cari pasangan terbaik:
  # 1. Prioritas sama universitas
  # 2. Fallback ke siapa saja yang pertama dalam antrian
  defp find_best_match([], _email, _uni), do: nil
  defp find_best_match(queue, email, university) do
    # Exclude diri sendiri dari pencarian
    candidates = Enum.reject(queue, fn {e, _, _} -> e == email end)

    # Coba match dengan universitas sama jika ada
    same_uni = if university do
      Enum.find(candidates, fn {_, u, _} -> u == university end)
    else
      nil
    end

    same_uni || List.first(candidates)
  end

  defp find_position(queue, email) do
    case Enum.find_index(queue, fn {e, _, _} -> e == email end) do
      nil -> 0
      idx -> idx + 1
    end
  end

  defp broadcast_queue_size(size) do
    Phoenix.PubSub.broadcast(
      AuthService.PubSub,
      "matchmaking:stats",
      {:queue_size, size}
    )
  end
end
