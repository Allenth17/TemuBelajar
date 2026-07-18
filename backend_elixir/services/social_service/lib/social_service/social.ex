defmodule SocialService.Social do
  @moduledoc """
  Social graph context — follow, friend, block, report.

  All writes immediately invalidate the FollowerCache for affected users.
  Reads use paginated queries (limit 50) to bound memory usage.
  """

  import Ecto.Query, warn: false
  require Logger
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
      from(f in Follow,
        where: f.follower_email == ^follower_email and f.followee_email == ^followee_email
      )
    )

    FollowerCache.invalidate(follower_email, followee_email)
    :ok
  end

  @doc "Returns paginated list of followers for an email."
  def list_followers(email, limit \\ 50, offset \\ 0) do
    Repo.all(
      from(f in Follow,
        where: f.followee_email == ^email,
        select: f.follower_email,
        limit: ^limit,
        offset: ^offset,
        order_by: [desc: f.inserted_at]
      )
    )
  end

  @doc "Returns paginated list of accounts the user follows."
  def list_following(email, limit \\ 50, offset \\ 0) do
    Repo.all(
      from(f in Follow,
        where: f.follower_email == ^email,
        select: f.followee_email,
        limit: ^limit,
        offset: ^offset,
        order_by: [desc: f.inserted_at]
      )
    )
  end

  @doc "Returns {follower_count, following_count}. Uses ETS cache."
  def fetch_counts(email), do: FollowerCache.get_counts(email)

  @doc "DB query for counts — called by FollowerCache on miss."
  def fetch_counts_from_db(email) do
    follower_count =
      Repo.one(from(f in Follow, where: f.followee_email == ^email, select: count()))

    following_count =
      Repo.one(from(f in Follow, where: f.follower_email == ^email, select: count()))

    {follower_count || 0, following_count || 0}
  end

  @doc "Returns up to 3 follower email addresses (for profile preview)."
  def followers_preview(email) do
    Repo.all(
      from(f in Follow,
        where: f.followee_email == ^email,
        select: f.follower_email,
        limit: 3,
        order_by: [desc: f.inserted_at]
      )
    )
  end

  @doc "Returns true if follower_email follows followee_email."
  def following?(follower_email, followee_email) do
    Repo.exists?(
      from(f in Follow,
        where: f.follower_email == ^follower_email and f.followee_email == ^followee_email
      )
    )
  end

  # ─── Friend Requests ────────────────────────────────────────────────────────

  @doc "Send a friend request. Returns error if already sent or already friends."
  def send_friend_request(from_email, to_email) do
    # Phase 7.17 — Since rejected requests are now kept with
    # `status = "rejected"` (see `respond_to_friend_request/3`), a naive
    # `on_conflict: :nothing` insert would silently no-op when a
    # requester re-sends after being rejected (the requester couldn't
    # distinguish "just sent" from "already pending after rejection").
    #
    # We could not express a *conditional* upsert through Ecto's
    # declarative `:on_conflict` (which would either always no-op or
    # always overwrite — the latter would rewind an `"accepted"` row
    # back to `"pending"` on a re-send). Instead we run a raw
    # `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE status = 'rejected'`
    # that:
    #
    #   • inserts a brand-new row if none exists;
    #   • re-arms a previously-rejected row to `"pending"` (re-enabling
    #     re-asking after a prior rejection);
    #   • leaves an existing `"pending"`/`"accepted"` row alone.
    #
    # The `ON CONFLICT` clause intentionally targets the directional
    # `(from_email, to_email)` PK (not the canonical `(LEAST, GREATEST)`
    # index added in 7.15) — a re-send should only re-arm if the same
    # person is asking the same person, not if the *other* side asks
    # (that's a brand-new request in its own direction). The canonical
    # index stops the symmetric pair insert on the table-level check.
    sql = """
    INSERT INTO friend_requests (from_email, to_email, status, inserted_at)
    VALUES ($1, $2, 'pending', NOW())
    ON CONFLICT (from_email, to_email)
    DO UPDATE SET status = 'pending', inserted_at = NOW()
    WHERE friend_requests.status = 'rejected'
    RETURNING "from_email", "to_email", "status", "inserted_at"
    """

    case Repo.query(sql, [from_email, to_email]) do
      {:ok, %Postgrex.Result{rows: [row | _], num_rows: n}} when n > 0 ->
        {:ok, row_to_friend_request(row)}

      {:ok, %Postgrex.Result{num_rows: 0}} ->
        # No INSERT and no re-arm happened — the row already existed and
        # was `"pending"` or `"accepted"`. Return the pre-existing row
        # to keep the function signature identical to the legacy return.
        case Repo.get_by(FriendRequest, from_email: from_email, to_email: to_email) do
          nil -> {:error, :conflict}
          req -> {:ok, req}
        end

      {:error, err} ->
        Logger.error(
          "[Social] send_friend_request (#{from_email} -> #{to_email}) failed: " <> inspect(err)
        )

        {:error, err}
    end
  end

  # Helper that turns the raw row list returned by Postgrex via
  # `Repo.query/2` into a `FriendRequest` struct. The column order in
  # the `RETURNING` clause is `(from_email, to_email, status,
  # inserted_at)`, matching this pattern; if the clause above ever
  # changes, update both sides together.
  defp row_to_friend_request([from_email, to_email, status, inserted_at]) do
    %FriendRequest{
      from_email: from_email,
      to_email: to_email,
      status: status,
      inserted_at: inserted_at
    }
  end

  @doc "Accept or reject a friend request. action = :accept | :reject"
  def respond_to_friend_request(from_email, to_email, :accept) do
    case Repo.get_by(FriendRequest, from_email: from_email, to_email: to_email) do
      nil ->
        {:error, :not_found}

      req ->
        # Phase 7.16 — Room/wrap the result of `Repo.update/1` so a
        # failed acceptance (validation error, DB outage, stale row)
        # actually surfaces to the caller instead of being silently
        # discarded. Previously the update return value was thrown away
        # and the caller would receive `:ok` even though the row was
        # never marked `accepted`, which would leave the auto-follows
        # below pointing at a still-pending request.
        case req
             |> FriendRequest.changeset(%{status: "accepted"})
             |> Repo.update() do
          {:ok, _updated} ->
            # Auto-follow both directions on friend acceptance
            follow(to_email, from_email)
            follow(from_email, to_email)
            :ok

          {:error, changeset} ->
            Logger.error(
              "[Social] accept friend_request (#{from_email} -> #{to_email}) update failed: " <>
                inspect(changeset.errors)
            )

            {:error, changeset}
        end
    end
  end

  def respond_to_friend_request(from_email, to_email, :reject) do
    # Phase 7.17 — Keep the rejected row instead of deleting it, so
    # there's an audit trail of the rejection. Before this change a
    # rejection silently deleted the request row, which combined with
    # `send_friend_request/2`'s `on_conflict: :nothing` made it
    # impossible for the requester to tell *why* a re-send was a no-op:
    # "already pending" and "I was rejected before" were
    # indistinguishable. Now the row stays around with `status =
    # "rejected"`, and `send_friend_request/2` below is updated to
    # re-arm a previously-rejected request on re-send (instead of
    # blindly no-op'ing), so the requester can re-poke a user who
    # previously rejected them.
    case Repo.get_by(FriendRequest, from_email: from_email, to_email: to_email) do
      nil ->
        {:error, :not_found}

      req ->
        req
        |> FriendRequest.changeset(%{status: "rejected"})
        |> Repo.update()
        |> case do
          {:ok, _updated} -> :ok
          {:error, changeset} -> {:error, changeset}
        end
    end
  end

  @doc "Unfriend — removes accepted request and mutual follows."
  def unfriend(email_a, email_b) do
    Repo.delete_all(
      from(r in FriendRequest,
        where:
          (r.from_email == ^email_a and r.to_email == ^email_b) or
            (r.from_email == ^email_b and r.to_email == ^email_a)
      )
    )

    unfollow(email_a, email_b)
    unfollow(email_b, email_a)
    :ok
  end

  @doc "Returns accepted friends for an email."
  def list_friends(email, limit \\ 50, offset \\ 0) do
    Repo.all(
      from(r in FriendRequest,
        where:
          r.status == "accepted" and
            (r.from_email == ^email or r.to_email == ^email),
        select: fragment("CASE WHEN from_email = ? THEN to_email ELSE from_email END", ^email),
        limit: ^limit,
        offset: ^offset,
        order_by: [desc: r.inserted_at]
      )
    )
  end

  @doc "Returns pending friend requests sent TO this user."
  def list_pending_requests(email) do
    Repo.all(
      from(r in FriendRequest,
        where: r.to_email == ^email and r.status == "pending",
        order_by: [desc: r.inserted_at]
      )
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
      from(b in Block,
        where: b.blocker_email == ^blocker_email and b.blocked_email == ^blocked_email
      )
    )

    :ok
  end

  @doc "Returns true if blocker has blocked blocked."
  def blocked?(blocker_email, blocked_email) do
    Repo.exists?(
      from(b in Block,
        where: b.blocker_email == ^blocker_email and b.blocked_email == ^blocked_email
      )
    )
  end

  @doc """
  Returns the set of emails that should NOT be matched with `email`:
  those `email` has blocked AND those who have blocked `email`.

  Both directions are unsafe for matchmaking — either side blocking the
  other should prevent the pair. Used by the matchmaking_service block-list
  guard (Phase 5.31).
  """
  def list_blocked_by(email) do
    # People this user has blocked
    blocked =
      Repo.all(from(b in Block, where: b.blocker_email == ^email, select: b.blocked_email))

    # People who have blocked this user
    blockers =
      Repo.all(from(b in Block, where: b.blocked_email == ^email, select: b.blocker_email))

    Enum.uniq(blocked ++ blockers)
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
