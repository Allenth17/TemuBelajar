defmodule SocialService.Social.Block do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key false
  schema "blocks" do
    field :blocker_email, :string, primary_key: true
    field :blocked_email, :string, primary_key: true
    timestamps(type: :utc_datetime, updated_at: false)
  end

  def changeset(block \\ %__MODULE__{}, attrs) do
    block
    |> cast(attrs, [:blocker_email, :blocked_email])
    |> validate_required([:blocker_email, :blocked_email])
    |> unique_constraint([:blocker_email, :blocked_email], name: :blocks_pkey)
  end
end
