# 💼 Case Study: SplitMaster Simple

**Мобильное приложение для учета и разделения совместных расходов с real-time чатами.**

Этот документ является техническим обзором (витриной) проекта **SplitMaster Simple**. Здесь описаны архитектурные решения, стек технологий и примеры реализации ключевых модулей, которые демонстрируют мой подход к Android-разработке.

---

## 🎯 О проекте и бизнес-логике

**SplitMaster Simple** решает проблему неудобного разделения чеков между друзьями. Приложение не просто записывает долги, а автоматически пересчитывает баланс каждого участника группы при добавлении новых трат, а также позволяет обсуждать эти траты во встроенном мессенджере.

**Ключевые технические вызовы, которые были решены в проекте:**
1. Синхронизация данных (расходы, сообщения) на всех устройствах в реальном времени.
2. Изоляция бизнес-логики математических расчетов от UI-слоя.
3. Управление сложным состоянием экранов (стягивание данных из разных коллекций базы данных).
4. Реализация кастомного, декларативного UI без использования сторонних тяжеловесных библиотек.

---

## 🏗 Архитектура и Технологический стек

Проект спроектирован с упором на масштабируемость и легкость тестирования. В основе лежит паттерн **MVVM (Model-View-ViewModel)** в связке с реактивным программированием.

*   **Язык:** Kotlin (с активным использованием Coroutines и Flow)
*   **UI Framework:** Jetpack Compose (Material Design 3)
*   **Слой данных (BaaS):** Firebase (Firestore, Authentication, Cloud Messaging)
*   **Навигация:** Compose Navigation
*   **Асинхронность:** `StateFlow`, `SharedFlow`, `viewModelScope`

---

## 💻 Разбор ключевых решений (Примеры кода)

Ниже приведены фрагменты исходного кода, демонстрирующие качество реализации.

### 1. Реактивное прослушивание базы данных (Firebase + StateFlow)

Вместо одноразовых HTTP-запросов, приложение использует слушатели Firestore, переведенные в Kotlin Flow. Это позволяет UI реагировать на любые изменения в базе моментально.

<details>
<summary><b>Развернуть код (Слушатель чатов)</b></summary>

```kotlin
// Во ViewModel мы подписываемся на изменения коллекции сообщений
fun startListeningForMessages(chatId: String) {
    messageListener?.remove() // Удаляем старый слушатель, если есть
    
    messageListener = db.collection("messages")
        .whereEqualTo("chatId", chatId)
        .orderBy("timestamp", Query.Direction.ASCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("ChatVM", "Ошибка прослушивания сообщений", error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val newMessages = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                }
                
                // Асинхронно обновляем стейт для Compose
                viewModelScope.launch {
                    _messages.value = newMessages
                }
            }
        }
}
```
</details>

### 2. Чистые функции расчета баланса (Изоляция логики)

Алгоритм, который вычисляет кто, кому и сколько должен, вынесен в отдельный объект `BalanceCalculator`. Он принимает на вход абстрактные данные (`List<Expense>`) и возвращает результат, ничего не зная об Android Framework. Это идеальный кандидат для Unit-тестирования.

<details>
<summary><b>Развернуть код (Алгоритм баланса)</b></summary>

```kotlin
object BalanceCalculator {
    fun calculateBalances(expenses: List<Expense>, memberIds: List<String>): Map<String, UserBalance> {
        val balances = memberIds.associateWith { UserBalance(userId = it) }.toMutableMap()
        
        expenses.forEach { expense ->
            if (expense.participants.isEmpty()) return@forEach
            val perPerson = expense.amount / expense.participants.size
            
            // 1. Плательщику возвращаем часть суммы, которую он покрыл за других
            balances[expense.paidBy]?.let { 
                it.balance += (expense.amount - perPerson) 
            }
            
            // 2. Списываем долю каждого участника с его баланса
            expense.participants.filter { it != expense.paidBy }.forEach { participantId ->
                balances[participantId]?.let { 
                    it.balance -= perPerson 
                }
            }
        }
        return balances
    }
}
```
</details>

### 3. Современный UI с Jetpack Compose

Все экраны написаны программно через Compose. Это позволило создать гибкую систему тем (Dark/Light mode, кастомные акцентные цвета) и избавиться от громоздких XML-файлов и `RecyclerView.Adapter`.

<details>
<summary><b>Развернуть код (Отрисовка элемента чата)</b></summary>

```kotlin
@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    // Динамическое определение цветов в зависимости от того, чье сообщение
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else if (isMine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = if (isMine && !isSelected) MaterialTheme.colorScheme.onPrimary 
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
```
</details>

---

## 🔒 Безопасность и "Мягкое удаление" (Soft Delete)

В приложении реализован сложный механизм управления чатами. Сообщения не удаляются из базы данных физически, если один пользователь нажимает "Удалить чат", так как история должна сохраниться у собеседника.

Используются метки времени `deletedForSender` и `historyDeletedAt`. Локальная ViewModel фильтрует входящие данные, скрывая их от пользователя, но сохраняя целостность БД.

---

## 📥 Скачать приложение

Если вы хотите протестировать приложение "вживую", вы можете скачать актуальный APK-файл из раздела релизов (основной репозиторий):

[**Скачать APK (GitHub Releases)**](https://github.com/TeskiOke/SplitMasterSimple-Release/releases)

Приложение представляет собой стабильный, современный продукт с плавной анимацией, предсказуемым поведением и отличной архитектурой, готовой к масштабированию и поддержке в команде.

