package com.lex.currentweatherapp.models

import java.io.Serializable

data class Weather(
    val description: String,
    val id: Int,
    val main: String,
    val icon: String
): Serializable
