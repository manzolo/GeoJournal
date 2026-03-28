FROM python:3.12-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends tzdata wget \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Bundle JS/CSS libraries (BSD-licensed) — no runtime CDN dependency
RUN mkdir -p static \
 && wget -q -O static/leaflet.css       https://unpkg.com/leaflet@1.9.4/dist/leaflet.css \
 && wget -q -O static/leaflet.js        https://unpkg.com/leaflet@1.9.4/dist/leaflet.js \
 && wget -q -O static/leaflet-omnivore.min.js \
      https://unpkg.com/@mapbox/leaflet-omnivore@0.3.4/leaflet-omnivore.min.js

COPY app.py .
COPY templates/ templates/

ENV BACKUP_PATH=/data/backup.zip
EXPOSE 8080

CMD ["python", "app.py"]
