defmodule SocialService.FollowerCache do
  @moduledoc """
  ETS-based cache for follower/following counts.

  Avoids hitting the DB on every profile view.
  Cache entries expire after @ttl_ms milliseconds (60 seconds).
  Writes (follow/unfollow) immediately invalidate the affected entries.

  RAM: ~200 bytes per entry × (active users) — tiny footprint.
  """

  use GenServer

  @table :social_follower_cache
  @ttl_ms 60_000

  # ─── Client API ─────────────────────────────────────────────────────────────

  def start_link(opts \\ []) do
    GenServer.start_link(__MODULE__, opts, name: __MODULE__)
  end

  @doc "Returns {follower_count, following_count} for email, using DB fallback."
  def get_counts(email) do
    now_ms = now()
    case :ets.lookup(@table, {:counts, email}) do
      [{_, counts, expires_at}] when now_ms < expires_at ->
        counts

      _ ->
        counts = SocialService.Social.fetch_counts_from_db(email)
        :ets.insert(@table, {{:counts, email}, counts, now() + @ttl_ms})
        counts
    end
  end

  @doc "Invalidates cache for both parties on follow/unfollow."
  def invalidate(email_a, email_b) do
    :ets.delete(@table, {:counts, email_a})
    :ets.delete(@table, {:counts, email_b})
  end

  # ─── GenServer callbacks ─────────────────────────────────────────────────────

  @impl true
  def init(_opts) do
    :ets.new(@table, [:named_table, :public, :set, read_concurrency: true])
    # Sweep expired entries every minute
    :timer.send_interval(@ttl_ms, :sweep)
    {:ok, %{}}
  end

  @impl true
  def handle_info(:sweep, state) do
    now = now()
    :ets.select_delete(@table, [{{:_, :_, :"$1"}, [{:<, :"$1", now}], [true]}])
    {:noreply, state}
  end

  defp now, do: :erlang.system_time(:millisecond)
end
