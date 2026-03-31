defmodule SocialService.Social.Follow do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key false
  schema "follows" do
    field :follower_email, :string, primary_key: true
    field :followee_email, :string, primary_key: true
    timestamps(type: :utc_datetime, updated_at: false)
  end

  def changeset(follow \\ %__MODULE__{}, attrs) do
    follow
    |> cast(attrs, [:follower_email, :followee_email])
    |> validate_required([:follower_email, :followee_email])
    |> validate_not_self(:follower_email, :followee_email)
    |> unique_constraint([:follower_email, :followee_email], name: :follows_pkey)
  end

  defp validate_not_self(changeset, field_a, field_b) do
    a = get_field(changeset, field_a)
    b = get_field(changeset, field_b)
    if a != nil and a == b do
      add_error(changeset, field_a, "cannot follow yourself")
    else
      changeset
    end
  end
end
