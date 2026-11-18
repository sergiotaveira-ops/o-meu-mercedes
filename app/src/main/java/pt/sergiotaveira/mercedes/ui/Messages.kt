package pt.sergiotaveira.mercedes.ui

object Messages {
    const val BLOCKED = "A Mercedes bloqueou temporariamente os pedidos. Tenta daqui a uns minutos."
    const val TIMEOUT = "O carro não respondeu."
    const val UNLOCK_FAILED = "PIN incorreto ou comando recusado."
    const val WRONG_LOGIN = "Email ou palavra-passe errados."
    const val NO_NETWORK = "Sem ligação à internet."
    const val LOCKED_OK = "Carro trancado."
    const val UNLOCKED_OK = "Carro destrancado."
    const val CLIMATE_ON_OK = "Pré-climatização ligada."
    const val CLIMATE_OFF_OK = "Pré-climatização desligada."
    const val SESSION_EXPIRED = "A sessão expirou. Entra de novo."
    const val NO_CAR = "Ainda não recebi dados do carro. Puxa para atualizar."
    fun refused(reason: String) = "O carro recusou o comando ($reason)."
    fun loginFailed(reason: String) = "Não foi possível entrar ($reason)."
}
