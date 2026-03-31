# Build stage
FROM elixir:1.14-alpine AS build

WORKDIR /app

RUN apk add --no-cache build-base git

RUN mix local.hex --force && \
    mix local.rebar --force

COPY mix.exs mix.lock ./
RUN mix deps.get --only prod

COPY . .
ENV MIX_ENV=prod
RUN mix compile

# Runtime stage
FROM elixir:1.14-alpine

WORKDIR /app

RUN apk add --no-cache openssl ncurses-libs curl

RUN mix local.hex --force && \
    mix local.rebar --force

COPY --from=build /app/_build /app/_build
COPY --from=build /app/deps /app/deps
COPY --from=build /app/mix.exs /app/mix.exs
COPY --from=build /app/mix.lock /app/mix.lock
COPY --from=build /app/config /app/config
COPY --from=build /app/lib /app/lib
COPY --from=build /app/priv /app/priv

ENV MIX_ENV=prod

EXPOSE 4006

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:4006/api/health || exit 1

CMD ["mix", "phx.server"]
