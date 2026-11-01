package com.vasilispngs.videoplayer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeComputers : ComputerDao {
    private val computers = MutableStateFlow(emptyList<Computer>())

    override fun observe(): Flow<List<Computer>> = computers

    override suspend fun find(host: String, share: String): Computer? = computers.value.firstOrNull { it.host == host && it.share == share }

    override suspend fun insert(computer: Computer) {
        computers.value += computer
    }

    override suspend fun delete(id: Long) {
        computers.value = computers.value.filterNot { it.id == id }
    }
}
