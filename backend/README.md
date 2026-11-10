# Backend del asistente CECAPI (Módulo 3)

Servidor FastAPI al que la app le manda las preguntas de IA; las claves viven solo aquí, nunca en el APK.
1. Instala: `pip install -r requirements.txt` (con el entorno virtual activado).
2. Copia `.env.example` como `.env` y pon `GEMINI_API_KEY` y `GROQ_API_KEY`; `.env` no se sube al repo.
3. Arranca: `uvicorn main:app --host 0.0.0.0 --port 8000`.
4. En la app, `AI_PROXY_BASE_URL` (app/build.gradle.kts) debe ser `http://<IP-de-tu-PC>:8000/preguntar`.
Endpoints: `POST /preguntar` `{"pregunta","contexto"}` → `{"respuesta","proveedor"}` · `GET /uso` · `GET /`.
Respaldo: primero Gemini; si falla, tarda más de `IA_TIEMPO_LIMITE_S` o agota su límite, contesta Groq.
Límites diarios en `.env` (`*_LIMITE_DIARIO`): avisa en el log al 80% y al 100% salta al respaldo.
Código: `main.py` (rutas), `proveedores.py` (Gemini, Groq y respaldo), `uso.py` (contador diario).
