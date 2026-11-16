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

La app lee las fichas de Firestore (colección `fichas`). Para que funcione hay que hacer esto una vez en la consola de Firebase del proyecto:

1. **Activar Firestore** (Compilación → Firestore Database → Crear base de datos).
2. **Publicar las reglas** de `firestore.rules` (pestaña Reglas de Firestore: pegar el contenido y publicar). Permiten leer solo fichas publicadas y no permiten escribir desde la app.
3. **Crear una cuenta de servicio** (Configuración del proyecto → Cuentas de servicio → Generar nueva clave privada) y guardar el contenido del archivo como el secreto `FIREBASE_SERVICE_ACCOUNT` del repositorio en GitHub. Ese archivo da permiso de escritura: no se sube al repositorio ni se comparte.

Con el secreto configurado, al fusionar en `main` GitHub ejecuta `herramientas/fichas/subir.py` y publica las fichas válidas. Mientras no exista, GitHub solo valida.

Hasta completar estos pasos, la app responde "La biblioteca de fichas no permite la lectura". Con la biblioteca activa pero sin la ficha de una moto, responde "Aún no tenemos la ficha oficial de esta moto".

## Fichas que piden los usuarios

Cuando una moto no está en la biblioteca, la app muestra el botón "Solicitar esta ficha". Cada solicitud suma 1 al contador de esa moto y año en la colección `solicitudes` de Firestore; un mismo teléfono solo cuenta una vez por moto.

Para ver cuáles son las más pedidas: en GitHub, pestaña **Actions → Fichas más solicitadas → Run workflow**. El resultado aparece en el resumen de la ejecución. Usa el mismo secreto `FIREBASE_SERVICE_ACCOUNT`. También se puede ejecutar `python3 herramientas/fichas/solicitudes.py`.

Las reglas de `firestore.rules` incluyen esta colección: si ya las habías publicado antes, hay que volver a publicarlas.
