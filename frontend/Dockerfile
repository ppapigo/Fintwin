FROM node:24-alpine AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts

COPY index.html vite.config.js ./
COPY src ./src
RUN npm run build

FROM nginx:1.28-alpine AS runtime

COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/snippets /etc/nginx/snippets
COPY nginx/templates /etc/nginx/config-templates
COPY nginx/docker-entrypoint.d/05-select-config.sh /docker-entrypoint.d/05-select-config.sh
COPY --from=build /app/dist /usr/share/nginx/html

RUN chmod 0755 /docker-entrypoint.d/05-select-config.sh \
    && mkdir -p /etc/nginx/templates \
    && rm -f /etc/nginx/conf.d/default.conf

ENV NGINX_ENVSUBST_FILTER=DOMAIN

EXPOSE 80 443

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1/nginx-health || exit 1
