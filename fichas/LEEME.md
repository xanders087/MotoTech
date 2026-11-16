# Biblioteca de fichas técnicas

Cada archivo de esta carpeta es la ficha de una moto, transcrita del manual oficial del fabricante.
La app mostrará estos datos tal cual, con el manual y la página de donde salió cada uno.

**Regla única:** aquí solo entra lo que está escrito en el manual. Si el manual no trae un dato, se deja en `null`. No se completa con valores de otra moto, de internet ni "típicos".

## Cómo agregar una moto

1. Ten a mano el manual oficial (del propietario o de servicio) de ese modelo y año.
2. Copia `_plantilla.json` y ponle el nombre `marca_modelo_cilindraje_desde-hasta.json`, en minúsculas y con guiones. Ejemplo de forma: `marca_modelo-x_150_2020-2023.json`. Si no aciertas el nombre, el validador te dice cuál debe ser.
3. Llena los campos (tabla de abajo).
4. Ejecuta el validador y corrige lo que señale:
   ```
   python3 herramientas/fichas/validar.py
   ```
5. Sube el archivo al repositorio. GitHub vuelve a validar cada cambio automáticamente.

## Campos

| Campo | Qué poner |
|---|---|
| `marca`, `modelo` | Como aparecen en la tarjeta de propiedad y en el selector de la app. |
| `cilindraje_cc` | Número entero, sin "cc". |
| `anio_desde`, `anio_hasta` | Años de modelo que cubre este manual. Si es un solo año, repite el mismo. |
| `manual.titulo` | Título del manual tal como figura en la portada. |
| `manual.edicion` | Código o edición impresa en el manual, si la tiene. Si no, `null`. |
| `manual.url` | Enlace `https://` al PDF en el sitio del fabricante, si existe. Si no, `null`. |
| `revision.revisado_por` | Quién cotejó la ficha contra el manual. |
| `revision.fecha` | Fecha de esa revisión, `AAAA-MM-DD`. |
| `ficha_tecnica.*.valor` | El valor **con sus unidades, copiado como está en el manual**. No conviertas unidades. |
| `ficha_tecnica.*.pagina` | Página del manual donde está ese valor. |
| `plan_mantenimiento` | Una entrada por cada fila de la tabla de mantenimiento periódico del manual. |
| `plan_mantenimiento[].componente` | Nombre de la tarea o pieza, como en el manual. |
| `plan_mantenimiento[].accion` | Qué se hace: cambiar, revisar, ajustar, lubricar… |
| `plan_mantenimiento[].intervalo_km` / `intervalo_meses` | Cada cuánto. Al menos uno de los dos; el otro puede ser `null`. |
| `plan_mantenimiento[].pagina` | Página de la tabla. |

Para un dato que el manual no trae, reemplaza todo el dato por `null`:

```json
"holgura_cadena": null
```

## Casos frecuentes

- **El manual cambia entre años.** Crea una ficha por cada rango de años. Los rangos de una misma moto no pueden cruzarse.
- **El manual da dos valores** (por ejemplo, presión con y sin pasajero). Escribe ambos en `valor`, con las palabras del manual.
- **La primera revisión tiene un intervalo distinto** (asentamiento). Regístrala como una tarea aparte, con su propio nombre.
- **Tareas "cada vez que se use" o "según estado".** No tienen intervalo en km ni meses, así que no van en el plan.

## Publicar en la app

`herramientas/fichas/subir.py` publica las fichas válidas en Firestore (colección `fichas`). Necesita la clave de una cuenta de servicio del proyecto de Firebase; en GitHub se configura como el secreto `FIREBASE_SERVICE_ACCOUNT` y la publicación ocurre sola al fusionar en `main`. Mientras ese secreto no exista, GitHub solo valida.
