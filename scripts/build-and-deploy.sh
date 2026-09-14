#!/usr/bin/env bash
# Automatiza el proceso manual de build+deploy de una imagen custom de Traccar
# (necesario porque prod corre `traccar/traccar:6.13` oficial y no hay registry
# configurado para hacer `docker push`). Reemplaza los pasos sueltos de
# gradle assemble + docker cp + zip + docker save/scp/load por un solo comando.
#
# No toca Coolify: solo deja la imagen cargada en el Docker del VPS. El cambio
# de `image:` + `pull_policy: never` y el redeploy siguen siendo manuales,
# como ya se hizo para stage — esa es la señal de "listo para publicar".
#
# Uso:
#   ./scripts/build-and-deploy.sh [tag]
#
# Si no se pasa [tag], se usa el short SHA del commit actual (debe estar en
# `master` o en la rama que quieras publicar).
#
# Variables opcionales:
#   VPS_HOST (default: 66.94.99.215)
#   VPS_USER (default: root)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

VERSION="${1:-$(git rev-parse --short HEAD)}"
VPS_HOST="${VPS_HOST:-66.94.99.215}"
VPS_USER="${VPS_USER:-root}"
IMAGE_TAG="nivix-traccar:${VERSION}"
OFFICIAL_IMAGE="traccar/traccar:6.13"

STAGE_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGE_DIR"; docker rm -f traccar-webassets-tmp >/dev/null 2>&1 || true' EXIT

echo "==> Versión a construir: ${VERSION}"

echo "==> [1/6] Compilando el servidor (./gradlew assemble)"
./gradlew assemble --no-daemon

echo "==> [2/6] Armando el payload en ${STAGE_DIR}"
mkdir -p "$STAGE_DIR"/{conf,lib,schema,templates,data,logs}
cp target/tracker-server.jar "$STAGE_DIR"/
cp target/lib/* "$STAGE_DIR"/lib/
cp schema/* "$STAGE_DIR"/schema/
cp -r templates/* "$STAGE_DIR"/templates/
cp setup/traccar.xml "$STAGE_DIR"/conf/traccar.xml

echo "==> [3/6] Reusando assets web de la imagen oficial (${OFFICIAL_IMAGE})"
docker create --name traccar-webassets-tmp "$OFFICIAL_IMAGE" >/dev/null
docker cp traccar-webassets-tmp:/opt/traccar/web "$STAGE_DIR"/web
docker cp traccar-webassets-tmp:/opt/traccar/templates/translations "$STAGE_DIR"/templates/translations
docker rm -f traccar-webassets-tmp >/dev/null

echo "==> [4/6] Empaquetando zip y construyendo la imagen (linux/amd64)"
ZIP_NAME="traccar-other-${VERSION}.zip"
(cd "$STAGE_DIR" && zip -q -r "${REPO_ROOT}/docker/${ZIP_NAME}" .)
docker build --platform linux/amd64 \
  --build-arg VERSION="${VERSION}" \
  -f docker/Dockerfile.alpine \
  -t "${IMAGE_TAG}" \
  docker/
rm -f "docker/${ZIP_NAME}"

echo "==> [5/6] Transfiriendo la imagen al VPS (${VPS_USER}@${VPS_HOST})"
TAR_PATH="/tmp/nivix-traccar-${VERSION}.tar.gz"
docker save "${IMAGE_TAG}" | gzip > "${TAR_PATH}"
scp "${TAR_PATH}" "${VPS_USER}@${VPS_HOST}:/root/"
rm -f "${TAR_PATH}"

echo "==> [6/6] Cargando la imagen en el Docker del VPS"
ssh "${VPS_USER}@${VPS_HOST}" "docker load -i /root/nivix-traccar-${VERSION}.tar.gz && rm /root/nivix-traccar-${VERSION}.tar.gz"

cat <<EOF

Listo. Imagen '${IMAGE_TAG}' cargada en el VPS.

Pendiente manual en Coolify (recurso Traccar):
  1. Cambiar 'image:' a ${IMAGE_TAG}
  2. Confirmar 'pull_policy: never'
  3. Deploy/Redeploy (esto corta GT06/WS brevemente)
EOF
