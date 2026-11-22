FROM python:3.13-slim
RUN pip install --no-cache-dir "PyJWT[crypto]==2.10.1"
WORKDIR /app
COPY feedback_server.py .
USER nobody
CMD ["python", "-u", "feedback_server.py"]
