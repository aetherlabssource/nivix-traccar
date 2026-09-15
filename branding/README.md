# Branding web (título + favicon)

Archivos fuente para el override de branding de la web de Traccar (ver `INFRAESTRUCTURA.md` §6.1 en la raíz del monorepo para el detalle completo del mecanismo y el gotcha de las rutas SPA).

- `favicon.ico` — multi-resolución (16/32/48/64/128/256), generado con Pillow desde `logo-nivix-traccar.png`.
- `apple-touch-icon-180x180.png` — mismo logo, 180x180.
- `index.html` — copia del `index.html` real que sirve la imagen de Traccar en uso, con el `<title>` cambiado a "Nivix Traccar" y un script que corrige el título en tiempo de ejecución (la SPA de React lo pisa en cada cambio de ruta).

## Desplegar

Se montan sobre el contenedor, no se copian dentro de la imagen:

```
/opt/traccar/override:/opt/traccar/override:ro
/opt/traccar/override/index.html:/opt/traccar/web/index.html:ro
```

Si Traccar cambia de versión y regeneran los assets compilados (`/assets/index-*.js`, `/assets/index-*.css`), hay que volver a sacar el `index.html` real (`curl https://app.nivix.com.co/index.html`) y reaplicar el `<title>` + script sobre esa versión nueva — los hashes de los assets cambian por build.
