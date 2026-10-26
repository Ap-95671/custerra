from datetime import date
from typing import Literal
from fastapi import FastAPI
from pydantic import BaseModel, ConfigDict, Field
from app.services.prediction_service import predict, MODEL
app=FastAPI(title='Custerra experimental research API',version='research-v1')
class HistoricalWeather(BaseModel):
    model_config=ConfigDict(extra='forbid',allow_inf_nan=False)
    region:Literal['BEJAIA','SIDI_BEL_ABBES']
    observedDate:date=Field(ge=date(2012,6,1),le=date(2012,9,30))
    temperatureC:float=Field(ge=-50,le=60)
    relativeHumidityPct:float=Field(ge=0,le=100)
    windKmh:float=Field(ge=0,le=200)
    rainMm:float=Field(ge=0,le=1000)
@app.get('/health')
def health():return {'status':'UP','mode':'RESEARCH_ONLY','modelVersion':MODEL['modelVersion']}
@app.post('/v1/research/fire-label')
def fire_label(weather:HistoricalWeather):return predict(weather.model_dump(mode='json'))
