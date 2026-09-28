package com.example.data.local

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {

    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    val activeTasks: Flow<List<TaskEntity>> = taskDao.getActiveTasks()

    suspend fun getTaskById(id: String): TaskEntity? {
        return taskDao.getTaskById(id)
    }

    suspend fun insertTask(task: TaskEntity) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateStep(id: String, step: String) {
        val current = taskDao.getTaskById(id)
        if (current != null) {
            taskDao.updateTask(current.copy(currentStep = step, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun markCompleted(id: String, details: String? = null) {
        val current = taskDao.getTaskById(id)
        if (current != null) {
            taskDao.updateTask(
                current.copy(
                    status = "COMPLETED",
                    currentStep = "Task completed & verified",
                    details = details ?: current.details,
                    verified = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun markFailed(id: String, error: String) {
        val current = taskDao.getTaskById(id)
        if (current != null) {
            taskDao.updateTask(
                current.copy(
                    status = "FAILED",
                    currentStep = "Failed: $error",
                    errorMessage = error,
                    verified = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun cancelTask(id: String) {
        taskDao.cancelTask(id)
    }

    suspend fun deleteTask(id: String) {
        taskDao.deleteTaskById(id)
    }

    suspend fun clearFinishedTasks() {
        taskDao.clearFinishedTasks()
    }
}
