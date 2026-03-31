defmodule TemuBelajar.Repo.Migrations.AddPerformanceIndexes do
  use Ecto.Migration

  def change do
    # Index untuk username lookup
    execute "CREATE INDEX IF NOT EXISTS users_username_index ON users (username)"
    
    # Index untuk verified status
    execute "CREATE INDEX IF NOT EXISTS users_verified_index ON users (verified)"
    
    # Index untuk otp_created_at untuk cleanup OTP expired
    execute "CREATE INDEX IF NOT EXISTS users_otp_created_at_index ON users (otp_created_at)"
    
    # Index untuk last_login
    execute "CREATE INDEX IF NOT EXISTS users_last_login_index ON users (last_login)"
    
    # Composite index untuk verified + otp_created_at (untuk query user yang belum verified)
    execute "CREATE INDEX IF NOT EXISTS users_verified_otp_created_at_index ON users (verified, otp_created_at)"
  end
end
