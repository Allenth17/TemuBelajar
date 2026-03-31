defmodule ApiGateway.Services do
  @moduledoc """
  Configuration module for microservice URLs.
  """

  def get_service_url(:auth_service) do
    Application.get_env(:api_gateway, :auth_service_url)
  end

  def get_service_url(:user_service) do
    Application.get_env(:api_gateway, :user_service_url)
  end

  def get_service_url(:email_service) do
    Application.get_env(:api_gateway, :email_service_url)
  end

  def get_service_url(:signaling_service) do
    Application.get_env(:api_gateway, :signaling_service_url)
  end

  def get_service_url(:matchmaking_service) do
    Application.get_env(:api_gateway, :matchmaking_service_url)
  end
end
