defmodule SocialService.Social.Report do
  use Ecto.Schema
  import Ecto.Changeset

  @valid_reasons ~w(spam harassment inappropriate_content impersonation other)

  schema "reports" do
    field :reporter_email, :string
    field :reported_email, :string
    field :reason, :string
    field :detail, :string
    timestamps(type: :utc_datetime, updated_at: false)
  end

  def changeset(report \\ %__MODULE__{}, attrs) do
    report
    |> cast(attrs, [:reporter_email, :reported_email, :reason, :detail])
    |> validate_required([:reporter_email, :reported_email, :reason])
    |> validate_inclusion(:reason, @valid_reasons)
    |> validate_length(:detail, max: 500)
  end
end
