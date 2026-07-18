defmodule SocialServiceWeb.SocialController do
  use SocialServiceWeb, :controller

  alias SocialService.Social

  # ─── Follow / Unfollow ────────────────────────────────────────────────────

  def follow(conn, %{"target" => target}) do
    with {:ok, caller} <- get_caller_email(conn) do
      case Social.follow(caller, target) do
        :ok -> json(conn, %{ok: true})
        {:error, cs} -> conn |> put_status(400) |> json(%{error: format_errors(cs)})
      end
    else
      _ -> unauthorized(conn)
    end
  end

  def unfollow(conn, %{"target" => target}) do
    with {:ok, caller} <- get_caller_email(conn) do
      Social.unfollow(caller, target)
      json(conn, %{ok: true})
    else
      _ -> unauthorized(conn)
    end
  end

  # Phase 7.18 — the `:internal` pipeline on the social routes already
  # recruits `SocialServiceWeb.Plugs.InternalAuth`, which validates the
  # shared `X-Internal-Secret` and injects `:caller_email` from
  # `X-Caller-Email`. The list endpoints below used to read `email` from
  # the path and return raw email lists without explicitly checking a
  # caller was present — that trusted the plug implicitly. We now require
  # `get_caller_email/1` to succeed so a misconfigured scope (or a future
  # route mounted outside the `:internal` pipeline) cannot expose the
  # social graph as a public enumeration oracle.
  def followers(conn, %{"email" => email} = params) do
    with {:ok, _caller} <- get_caller_email(conn) do
      limit = parse_int(params["limit"], 50)
      offset = parse_int(params["offset"], 0)
      emails = Social.list_followers(email, limit, offset)
      {follower_count, following_count} = Social.fetch_counts(email)

      json(conn, %{
        email: email,
        followers: emails,
        follower_count: follower_count,
        following_count: following_count
      })
    else
      _ -> unauthorized(conn)
    end
  end

  def following(conn, %{"email" => email} = params) do
    with {:ok, _caller} <- get_caller_email(conn) do
      limit = parse_int(params["limit"], 50)
      offset = parse_int(params["offset"], 0)
      emails = Social.list_following(email, limit, offset)
      json(conn, %{email: email, following: emails})
    else
      _ -> unauthorized(conn)
    end
  end

  def profile_social(conn, %{"email" => email}) do
    with {:ok, caller} <- get_caller_email(conn) do
      {follower_count, following_count} = Social.fetch_counts(email)
      preview = Social.followers_preview(email)
      caller_follows = Social.following?(caller, email)

      json(conn, %{
        email: email,
        follower_count: follower_count,
        following_count: following_count,
        followed_by_preview: preview,
        you_follow: caller_follows
      })
    else
      _ -> unauthorized(conn)
    end
  end

  # ─── Friend Requests ──────────────────────────────────────────────────────

  def send_friend_request(conn, %{"target" => target}) do
    with {:ok, caller} <- get_caller_email(conn) do
      case Social.send_friend_request(caller, target) do
        {:ok, _} -> json(conn, %{ok: true})
        {:error, cs} -> conn |> put_status(400) |> json(%{error: format_errors(cs)})
      end
    else
      _ -> unauthorized(conn)
    end
  end

  def respond_friend_request(conn, %{"from" => from, "action" => action}) do
    with {:ok, caller} <- get_caller_email(conn) do
      act = if action == "accept", do: :accept, else: :reject

      case Social.respond_to_friend_request(from, caller, act) do
        :ok -> json(conn, %{ok: true})
        {:error, :not_found} -> conn |> put_status(404) |> json(%{error: "Request not found"})
      end
    else
      _ -> unauthorized(conn)
    end
  end

  def unfriend(conn, %{"target" => target}) do
    with {:ok, caller} <- get_caller_email(conn) do
      Social.unfriend(caller, target)
      json(conn, %{ok: true})
    else
      _ -> unauthorized(conn)
    end
  end

  def friends(conn, %{"email" => email} = params) do
    with {:ok, _caller} <- get_caller_email(conn) do
      limit = parse_int(params["limit"], 50)
      offset = parse_int(params["offset"], 0)
      friends = Social.list_friends(email, limit, offset)
      json(conn, %{email: email, friends: friends})
    else
      _ -> unauthorized(conn)
    end
  end

  def pending_requests(conn, _params) do
    with {:ok, caller} <- get_caller_email(conn) do
      requests = Social.list_pending_requests(caller)
      json(conn, %{requests: requests})
    else
      _ -> unauthorized(conn)
    end
  end

  # ─── Block ────────────────────────────────────────────────────────────────

  def block(conn, %{"target" => target}) do
    with {:ok, caller} <- get_caller_email(conn) do
      case Social.block(caller, target) do
        :ok -> json(conn, %{ok: true})
        {:error, cs} -> conn |> put_status(400) |> json(%{error: format_errors(cs)})
      end
    else
      _ -> unauthorized(conn)
    end
  end

  def unblock(conn, %{"target" => target}) do
    with {:ok, caller} <- get_caller_email(conn) do
      Social.unblock(caller, target)
      json(conn, %{ok: true})
    else
      _ -> unauthorized(conn)
    end
  end

  # ─── Report ───────────────────────────────────────────────────────────────

  def report(conn, %{"target" => target, "reason" => reason} = params) do
    with {:ok, caller} <- get_caller_email(conn) do
      case Social.report_user(caller, target, reason, params["detail"]) do
        :ok -> json(conn, %{ok: true})
        {:error, cs} -> conn |> put_status(400) |> json(%{error: format_errors(cs)})
      end
    else
      _ -> unauthorized(conn)
    end
  end

  # ─── Matchmaking Guard (internal) ────────────────────────────────────────

  def should_exclude(conn, %{"email_a" => a, "email_b" => b}) do
    json(conn, %{exclude: Social.should_exclude?(a, b)})
  end

  # Phase 5.31 — matchmaking_service fetches the full blocked-set for a
  # caller in one round-trip (then caches it for 30s) instead of calling
  # should_exclude? once per candidate. Returns both directions of the
  # block relation so neither side of a block can be matched with the
  # other. Internal-only, lives behind the :internal pipeline like
  # should_exclude.
  def blocked_by(conn, %{"email" => email}) do
    json(conn, %{email: email, blocked: Social.list_blocked_by(email)})
  end

  # ─── Helpers ─────────────────────────────────────────────────────────────

  defp get_caller_email(conn) do
    case conn.assigns[:caller_email] do
      email when is_binary(email) and email != "" -> {:ok, email}
      _ -> {:error, :unauthorized}
    end
  end

  defp unauthorized(conn) do
    conn |> put_status(401) |> json(%{error: "Unauthorized"})
  end

  defp parse_int(nil, default), do: default

  defp parse_int(v, default) do
    case Integer.parse(v) do
      {n, _} -> n
      :error -> default
    end
  end

  defp format_errors(changeset) do
    Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
      Enum.reduce(opts, msg, fn {key, value}, acc ->
        String.replace(acc, "%{#{key}}", to_string(value))
      end)
    end)
  end
end
