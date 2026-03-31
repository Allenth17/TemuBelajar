# Build stage
FROM elixir:1.14-alpine AS build

WORKDIR /app

# Install build dependencies
RUN apk add --no-cache build-base git

# Install hex and rebar
RUN mix local.hex --force && \
    mix local.rebar --force

# Copy mix files
COPY mix.exs mix.lock ./

# Install dependencies
RUN mix deps.get --only $MIX_ENV

# Copy source code
COPY . .

# Compile application
RUN mix compile

# Runtime stage
FROM elixir:1.14-alpine

WORKDIR /app

# Install runtime dependencies
RUN apk add --no-cache openssl ncurses-libs curl

# Install hex and rebar
RUN mix local.hex --force && \
    mix local.rebar --force

# Copy compiled application from build stage
COPY --from=build /app/_build /app/_build
COPY --from=build /app/deps /app/deps
COPY --from=build /app/mix.exs /app/mix.exs
COPY --from=build /app/mix.lock /app/mix.lock
COPY --from=build /app/config /app/config
COPY --from=build /app/lib /app/lib
COPY --from=build /app/priv /app/priv

# Set environment
ENV MIX_ENV=prod

# Expose port
EXPOSE 4002

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:4002/api/health || exit 1

# Run application
CMD ["mix", "phx.server"]
