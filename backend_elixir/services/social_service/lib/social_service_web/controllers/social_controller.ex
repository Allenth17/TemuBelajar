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
    end
  end

  def unfollow(conn, %{"target" => target}) do
    with {:ok, caller} <- get_caller_email(conn) do
      Social.unfollow(caller, target)
      json(conn, %{ok: true})
    end
  end

  def followers(conn, %{"email" => email} = params) do
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
  end

  def following(conn, %{"email" => email} = params) do
    limit = parse_int(params["limit"], 50)
    offset = parse_int(params["offset"], 0)
    emails = Social.list_following(email, limit, offset)
    json(conn, %{email: email, following: emails})
  end

  def profile_social(conn, %{"email" => email}) do
    {follower_count, following_count} = Social.fetch_counts(email)
    preview = Social.followers_preview(email)

    caller_follows =
      case get_caller_email(conn) do
        {:ok, caller} -> Social.following?(caller, email)
        _ -> false
      end

    json(conn, %{
      email: email,
      follower_count: follower_count,
      following_count: following_count,
      followed_by_preview: preview,
      you_follow: caller_follows
    })
  end

  # ─── Friend Requests ──────────────────────────────────────────────────────

  def send_friend_request(conn, %{"target" => target}) do
    with {:ok, caller} <- get_caller_email(conn) do
      case Social.send_friend_request(caller, target) do
        {:ok, _} -> json(conn, %{ok: true})
        {:error, cs} -> conn |> put_status(400) |> json(%{error: format_errors(cs)})
      end
    end
  end

  def respond_friend_request(conn, %{"from" => from, "action" => action}) do
    with {:ok, caller} <- get_caller_email(conn) do
      act = if action == "accept", do: :accept, else: :reject
      case Social.respond_to_friend_request(from, caller, act) do
        :ok -> json(conn, %{ok: true})
        {:error, :not_found} -> conn |> put_status(404) |> json(%{error: "Request not found"})
      end
    end
  end

  def unfriend(conn, %{"target" => target}) do
    with {:ok, caller} <- get_caller_email(conn) do
      Social.unfriend(caller, target)
      json(conn, %{ok: true})
    end
  end

  def friends(conn, %{"email" => email} = params) do
    limit = parse_int(params["limit"], 50)
    offset = parse_int(params["offset"], 0)
    friends = Social.list_friends(email, limit, offset)
    json(conn, %{email: email, friends: friends})
  end

  def pending_requests(conn, _params) do
    with {:ok, caller} <- get_caller_email(conn) do
      requests = Social.list_pending_requests(caller)
      json(conn, %{requests: requests})
    end
  end

  # ─── Block ────────────────────────────────────────────────────────────────

  def block(conn, %{"target" => target}) do
    with {:ok, caller} <- get_caller_email(conn) do
      case Social.block(caller, target) do
        :ok -> json(conn, %{ok: true})
        {:error, cs} -> conn |> put_status(400) |> json(%{error: format_errors(cs)})
      end
    end
  end

  def unblock(conn, %{"target" => target}) do
    with {:ok, caller} <- get_caller_email(conn) do
      Social.unblock(caller, target)
      json(conn, %{ok: true})
    end
  end

  # ─── Report ───────────────────────────────────────────────────────────────

  def report(conn, %{"target" => target, "reason" => reason} = params) do
    with {:ok, caller} <- get_caller_email(conn) do
      case Social.report_user(caller, target, reason, params["detail"]) do
        :ok -> json(conn, %{ok: true})
        {:error, cs} -> conn |> put_status(400) |> json(%{error: format_errors(cs)})
      end
    end
  end

  # ─── Matchmaking Guard (internal) ────────────────────────────────────────

  def should_exclude(conn, %{"email_a" => a, "email_b" => b}) do
    json(conn, %{exclude: Social.should_exclude?(a, b)})
  end

  # ─── Helpers ─────────────────────────────────────────────────────────────

  defp get_caller_email(conn) do
    case get_req_header(conn, "x-caller-email") do
      [email | _] when email != "" -> {:ok, email}
      _ -> {:error, :unauthorized}
    end
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
