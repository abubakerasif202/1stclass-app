package com.example.domain.repository

import com.example.model.Driver

interface DriverRepository {
    suspend fun getPrototypeDriver(): Driver?
    suspend fun getDriver(id: String): Driver?
}
