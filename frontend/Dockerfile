# ---------- build ----------
FROM node:24-alpine@sha256:a34ab6d7f6f4813f4d0c5469d74685604b962fbdbf5793a7bc7b4998ecb867c8 AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---------- runtime: nginx sem root ----------
FROM nginxinc/nginx-unprivileged:1.31-alpine@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e
# Correções de segurança do Alpine que a imagem oficial ainda não trouxe (o scan do CI barra HIGH/CRITICAL corrigíveis);
# curl não é usado pelo nginx: sai da imagem (menos superfície). Depois, de volta ao usuário sem privilégios.
USER root
RUN apk upgrade --no-cache && apk del --no-cache curl
USER 101
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080 8443
