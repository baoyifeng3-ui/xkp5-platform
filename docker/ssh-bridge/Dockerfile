FROM golang:1.22-alpine AS build
WORKDIR /src
COPY tools/ssh-bridge/go.mod ./
COPY tools/ssh-bridge/main.go ./
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w" -o /out/xkp-ssh-bridge .

FROM alpine:3.20
RUN apk add --no-cache ca-certificates openssh-client
COPY --from=build /out/xkp-ssh-bridge /usr/local/bin/xkp-ssh-bridge
EXPOSE 19245
ENTRYPOINT ["/usr/local/bin/xkp-ssh-bridge"]
