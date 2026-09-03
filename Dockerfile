FROM azul/zulu-openjdk-alpine:26-latest AS build
 
WORKDIR /src

COPY . .
 
RUN ./kotlin build
