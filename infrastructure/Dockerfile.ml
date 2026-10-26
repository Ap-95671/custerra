FROM python:3.14-slim
WORKDIR /app
COPY ml-service/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt && useradd --system research
COPY ml-service/app ./app
COPY ml-service/artifacts/model.json ./artifacts/model.json
USER research
EXPOSE 8001
CMD ["python","-m","uvicorn","app.main:app","--host","0.0.0.0","--port","8001"]
