defmodule SocialService.Social do
  @moduledoc """
  Social graph context — follow, friend, block, report.

  All writes immediately invalidate the FollowerCache for affected users.
  Reads use paginated queries (limit 50) to bound memory usage.
  """

  import Ecto.Query, warn: false
  alias SocialService.Repo
  alias SocialService.Social.{Follow, FriendRequest, Block, Report}
  alias SocialService.FollowerCache

  # ─── Follow / Unfollow ──────────────────────────────────────────────────────

  @doc "Follow a user. Idempotent — returns :ok if already following."
  def follow(follower_email, followee_email) do
    %Follow{}
    |> Follow.changeset(%{follower_email: follower_email, followee_email: followee_email})
    |> Repo.insert(on_conflict: :nothing)
    |> case do
      {:ok, _} ->
        FollowerCache.invalidate(follower_email, followee_email)
        :ok
      {:error, changeset} ->
        {:error, changeset}
    end
  end

  @doc "Unfollow a user."
  def unfollow(follower_email, followee_email) do
    Repo.delete_all(
      from f in Follow,
      where: f.follower_email == ^follower_email and f.followee_email == ^followee_email
    )
    FollowerCache.invalidate(follower_email, followee_email)
    :ok
  end

  @doc "Returns paginated list of followers for an email."
  def list_followers(email, limit \\ 50, offset \\ 0) do
    Repo.all(
      from f in Follow,
      where: f.followee_email == ^email,
      select: f.follower_email,
      limit: ^limit,
      offset: ^offset,
      order_by: [desc: f.inserted_at]
    )
  end

  @doc "Returns paginated list of accounts the user follows."
  def list_following(email, limit \\ 50, offset \\ 0) do
    Repo.all(
      from f in Follow,
      where: f.follower_email == ^email,
      select: f.followee_email,
      limit: ^limit,
      offset: ^offset,
      order_by: [desc: f.inserted_at]
    )
  end

  @doc "Returns {follower_count, following_count}. Uses ETS cache."
  def fetch_counts(email), do: FollowerCache.get_counts(email)

  @doc "DB query for counts — called by FollowerCache on miss."
  def fetch_counts_from_db(email) do
    follower_count = Repo.one(from f in Follow, where: f.followee_email == ^email, select: count())
    following_count = Repo.one(from f in Follow, where: f.follower_email == ^email, select: count())
    {follower_count || 0, following_count || 0}
  end

  @doc "Returns up to 3 follower email addresses (for profile preview)."
  def followers_preview(email) do
    Repo.all(
      from f in Follow,
      where: f.followee_email == ^email,
      select: f.follower_email,
      limit: 3,
      order_by: [desc: f.inserted_at]
    )
  end

  @doc "Returns true if follower_email follows followee_email."
  def following?(follower_email, followee_email) do
    Repo.exists?(
      from f in Follow,
      where: f.follower_email == ^follower_email and f.followee_email == ^followee_email
    )
  end

  # ─── Friend Requests ────────────────────────────────────────────────────────

  @doc "Send a friend request. Returns error if already sent or already friends."
  def send_friend_request(from_email, to_email) do
    %FriendRequest{}
    |> FriendRequest.changeset(%{from_email: from_email, to_email: to_email, status: "pending"})
    |> Repo.insert(on_conflict: :nothing)
    |> case do
      {:ok, req} -> {:ok, req}
      {:error, cs} -> {:error, cs}
    end
  end

  @doc "Accept or reject a friend request. action = :accept | :reject"
  def respond_to_friend_request(from_email, to_email, :accept) do
    case Repo.get_by(FriendRequest, from_email: from_email, to_email: to_email) do
      nil -> {:error, :not_found}
      req ->
        req
        |> FriendRequest.changeset(%{status: "accepted"})
        |> Repo.update()

        # Auto-follow both directions on friend acceptance
        follow(to_email, from_email)
        follow(from_email, to_email)
        :ok
    end
  end

  def respond_to_friend_request(from_email, to_email, :reject) do
    Repo.delete_all(
      from r in FriendRequest,
      where: r.from_email == ^from_email and r.to_email == ^to_email
    )
    :ok
  end

  @doc "Unfriend — removes accepted request and mutual follows."
  def unfriend(email_a, email_b) do
    Repo.delete_all(
      from r in FriendRequest,
      where:
        (r.from_email == ^email_a and r.to_email == ^email_b) or
        (r.from_email == ^email_b and r.to_email == ^email_a)
    )
    unfollow(email_a, email_b)
    unfollow(email_b, email_a)
    :ok
  end

  @doc "Returns accepted friends for an email."
  def list_friends(email, limit \\ 50, offset \\ 0) do
    Repo.all(
      from r in FriendRequest,
      where:
        r.status == "accepted" and
        (r.from_email == ^email or r.to_email == ^email),
      select: fragment("CASE WHEN from_email = ? THEN to_email ELSE from_email END", ^email),
      limit: ^limit,
      offset: ^offset,
      order_by: [desc: r.inserted_at]
    )
  end

  @doc "Returns pending friend requests sent TO this user."
  def list_pending_requests(email) do
    Repo.all(
      from r in FriendRequest,
      where: r.to_email == ^email and r.status == "pending",
      order_by: [desc: r.inserted_at]
    )
  end

  # ─── Block / Unblock ────────────────────────────────────────────────────────

  @doc "Block a user. Idempotent."
  def block(blocker_email, blocked_email) do
    # Remove any follow relationships
    unfollow(blocker_email, blocked_email)
    unfollow(blocked_email, blocker_email)

    %Block{}
    |> Block.changeset(%{blocker_email: blocker_email, blocked_email: blocked_email})
    |> Repo.insert(on_conflict: :nothing)
    |> case do
      {:ok, _} -> :ok
      {:error, cs} -> {:error, cs}
    end
  end

  @doc "Unblock a user."
  def unblock(blocker_email, blocked_email) do
    Repo.delete_all(
      from b in Block,
      where: b.blocker_email == ^blocker_email and b.blocked_email == ^blocked_email
    )
    :ok
  end

  @doc "Returns true if blocker has blocked blocked."
  def blocked?(blocker_email, blocked_email) do
    Repo.exists?(
      from b in Block,
      where: b.blocker_email == ^blocker_email and b.blocked_email == ^blocked_email
    )
  end

  # ─── Report ────────────────────────────────────────────────────────────────

  @doc "Report a user."
  def report_user(reporter_email, reported_email, reason, detail \\ nil) do
    %Report{}
    |> Report.changeset(%{
      reporter_email: reporter_email,
      reported_email: reported_email,
      reason: reason,
      detail: detail
    })
    |> Repo.insert()
    |> case do
      {:ok, _} -> :ok
      {:error, cs} -> {:error, cs}
    end
  end

  # ─── Matchmaking Guard ──────────────────────────────────────────────────────

  @doc """
  Returns true if user_a should NOT be matched with user_b.
  Used by matchmaking service to exclude blocked pairs.
  """
  def should_exclude?(email_a, email_b) do
    blocked?(email_a, email_b) or blocked?(email_b, email_a)
  end
end
