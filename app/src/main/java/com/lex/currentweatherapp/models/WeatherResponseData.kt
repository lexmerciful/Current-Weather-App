package com.lex.currentweatherapp.models

import java.io.Serializable

data class WeatherResponseData(
    var coord: Coord,
    var weather: List<Weather>,
    val base: String,
    val main: Main,
    val visibility: Int,
    val wind: Wind,
    val rain: Rain,
    val clouds: Clouds,
    val dt: Int,
    val sys: Sys,
    val timezone: Long,
    val id: Int,
    val name: String,
    val cod: Int
): Serializable
