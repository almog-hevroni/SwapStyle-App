package com.example.swapstyleproject.data.repository.base

import com.example.swapstyleproject.data.repository.Notification.NotificationRepository
import com.example.swapstyleproject.data.repository.Notification.NotificationRepositoryImpl
import com.example.swapstyleproject.data.repository.auth.AuthRepository
import com.example.swapstyleproject.data.repository.auth.AuthRepositoryImpl
import com.example.swapstyleproject.data.repository.user.UserRepository
import com.example.swapstyleproject.data.repository.item.ItemRepository
import com.example.swapstyleproject.data.repository.item.ItemRepositoryImpl
import com.example.swapstyleproject.data.repository.swap.SwapRepository
import com.example.swapstyleproject.data.repository.swap.SwapRepositoryImpl
import com.example.swapstyleproject.data.repository.user.UserRepositoryImpl

//Repository Pattern - The code divides access to data according to logical areas:
//AuthRepository - Managing user registration, login and authentication
//UserRepository - Managing user profiles
//ItemRepository - Managing clothing items
//SwapRepository - Managing swaps and offers
interface FirebaseRepository {
    val authRepository: AuthRepository
    val userRepository: UserRepository
    val itemRepository: ItemRepository
    val swapRepository: SwapRepository
    val notificationRepository: NotificationRepository

    companion object {
        @Volatile
        private var instance: FirebaseRepository? = null

        fun getInstance(): FirebaseRepository {
            return instance ?: synchronized(this) {
                instance ?: FirebaseRepositoryImpl().also { instance = it }
            }
        }
    }
}

class FirebaseRepositoryImpl : FirebaseRepository {
    override val authRepository: AuthRepository = AuthRepositoryImpl()
    override val userRepository: UserRepository = UserRepositoryImpl()
    override val itemRepository: ItemRepository = ItemRepositoryImpl()
    override val swapRepository: SwapRepository = SwapRepositoryImpl()
    override val notificationRepository: NotificationRepository = NotificationRepositoryImpl()

}


