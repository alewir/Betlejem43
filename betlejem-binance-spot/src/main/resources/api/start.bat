docker pull swaggerapi/swagger-ui

docker run -p 8080:8080 -e SWAGGER_JSON=/app/spot_api.yaml -v %cd%:/app swaggerapi/swagger-ui:latest
