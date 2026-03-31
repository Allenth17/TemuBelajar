defmodule SocialService.Social.FriendRequest do
  use Ecto.Schema
  import Ecto.Changeset

  @valid_statuses ~w(pending accepted rejected)

  @primary_key false
  schema "friend_requests" do
    field :from_email, :string, primary_key: true
    field :to_email, :string, primary_key: true
    field :status, :string, default: "pending"
    timestamps(type: :utc_datetime, updated_at: false)
  end

  def changeset(req \\ %__MODULE__{}, attrs) do
    req
    |> cast(attrs, [:from_email, :to_email, :status])
    |> validate_required([:from_email, :to_email])
    |> validate_inclusion(:status, @valid_statuses)
    |> unique_constraint([:from_email, :to_email], name: :friend_requests_pkey)
  end
end
