#!/bin/sh
set -eu

case "${NGINX_TLS_MODE:-}" in
  http)
    cp /etc/nginx/config-templates/default-http.conf.template /etc/nginx/templates/default.conf.template
    ;;
  https)
    cp /etc/nginx/config-templates/default-https.conf.template /etc/nginx/templates/default.conf.template
    ;;
  *)
    echo "NGINX_TLS_MODE must be either 'http' or 'https'." >&2
    exit 1
    ;;
esac
