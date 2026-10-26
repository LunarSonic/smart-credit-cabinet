"""FastAPI-приложение ML-сервиса.

Запуск локально:  uvicorn examples.serve:app --host 127.0.0.1 --port 8001
В Docker:         uvicorn examples.serve:app --host 0.0.0.0 --port 8000
Swagger:          http://127.0.0.1:8001/docs
"""

from fastapi import FastAPI

from .fastapi_router import router
from .decision_router import router as decision_router
from .backend_router import router as backend_router

app = FastAPI(title="Synthetic credit scoring DEMO — do not use for real lending")


@app.get("/health", tags=["infra"])
def health() -> dict:
    return {"status": "ok"}


app.include_router(backend_router)       # POST /decide  ← вызывает Java-бэкенд
app.include_router(router)               # POST /internal/scoring/predict
app.include_router(decision_router)      # POST /internal/decision/evaluate
