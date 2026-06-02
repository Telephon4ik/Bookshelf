/**
 * Star Rating Component with half-star support (0.5 steps)
 * Компонент для отображения и выбора рейтинга звёздами с поддержкой половинок
 *
 * @class StarRating
 * @description Позволяет пользователю выбирать рейтинг кликами по звёздам
 *              с точностью до 0.5 (левая половина звезды = 0.5, правая = 1)
 */

/**
 * Конструктор компонента рейтинга
 * @param {string} containerId - ID контейнера со звёздами
 * @param {string} inputId - ID скрытого input для хранения значения
 * @param {string} textId - ID элемента для отображения текстового значения
 * @param {string} clearBtnId - ID кнопки очистки рейтинга
 * @param {number} [initialRating=0] - Начальное значение рейтинга
 */
class StarRating {
    constructor(containerId, inputId, textId, clearBtnId, initialRating = 0) {
        /** @type {HTMLElement} - Контейнер со звёздами */
        this.container = document.getElementById(containerId);
        /** @type {HTMLInputElement} - Скрытое поле для хранения значения */
        this.input = document.getElementById(inputId);
        /** @type {HTMLElement} - Элемент для отображения текста рейтинга */
        this.textSpan = document.getElementById(textId);
        /** @type {HTMLElement} - Кнопка очистки рейтинга */
        this.clearBtn = document.getElementById(clearBtnId);
        /** @type {number} - Текущее значение рейтинга */
        this.currentRating = initialRating;
        /** @type {Array<HTMLElement>} - Массив звёзд */
        this.stars = Array.from(this.container.querySelectorAll('.star'));
        this.init();
    }

    /**
     * Инициализация компонента: привязка событий и установка начального рейтинга
     */
    init() {
        this.stars.forEach((star, idx) => {
            star.addEventListener('mousemove', (e) => {
                const rect = star.getBoundingClientRect();
                const mouseX = e.clientX - rect.left;
                const isLeftHalf = mouseX < rect.width / 2;

                let hoverValue = idx + 1;
                if (isLeftHalf) {
                    hoverValue = idx + 0.5;
                } else {
                    hoverValue = idx + 1;
                }

                this.updateStarsHover(hoverValue);
            });

            star.addEventListener('click', (e) => {
                const rect = star.getBoundingClientRect();
                const mouseX = e.clientX - rect.left;
                const isLeftHalf = mouseX < rect.width / 2;

                let newRating = idx + 1;
                if (isLeftHalf) {
                    newRating = idx + 0.5;
                } else {
                    newRating = idx + 1;
                }

                this.setRating(newRating);
            });
        });

        this.container.addEventListener('mouseleave', () => {
            this.updateStarsDisplay(this.currentRating);
        });

        if (this.clearBtn) {
            this.clearBtn.addEventListener('click', () => {
                this.setRating(0);
            });
        }

        if (this.input && this.input.value) {
            const initial = parseFloat(this.input.value);
            if (!isNaN(initial) && initial > 0) {
                this.setRating(initial);
            } else {
                this.updateStarsDisplay(0);
            }
        } else {
            this.updateStarsDisplay(0);
        }
    }

    /**
     * Обновляет отображение звёзд при наведении (preview)
     * @param {number} value - Значение рейтинга для предпросмотра
     */
    updateStarsHover(value) {
        for (let i = 0; i < this.stars.length; i++) {
            const star = this.stars[i];
            const starNum = i + 1;
            const img = star.querySelector('img');

            if (value >= starNum) {
                img.src = '/icons/rating/icon-star-filled.png';
            } else if (value >= starNum - 0.5 && value < starNum) {
                img.src = '/icons/rating/icon-star-half.png';
            } else {
                img.src = '/icons/rating/icon-star-empty.png';
            }
        }
    }

    /**
     * Обновляет отображение звёзд в соответствии с установленным рейтингом
     * @param {number} value - Текущее значение рейтинга
     */
    updateStarsDisplay(value) {
        for (let i = 0; i < this.stars.length; i++) {
            const star = this.stars[i];
            const starNum = i + 1;
            const img = star.querySelector('img');

            if (value >= starNum) {
                img.src = '/icons/rating/icon-star-filled.png';
            } else if (value >= starNum - 0.5 && value < starNum) {
                img.src = '/icons/rating/icon-star-half.png';
            } else {
                img.src = '/icons/rating/icon-star-empty.png';
            }
        }

        if (this.textSpan) {
            if (value === 0) {
                this.textSpan.textContent = 'Не оценено';
                if (this.clearBtn) this.clearBtn.style.display = 'none';
            } else {
                const displayRating = value % 1 === 0 ? value : value.toFixed(1);
                this.textSpan.innerHTML = `<img src="/icons/rating/icon-star-filled.png" alt="★"
                 style="width: 16px; height: 16px; margin-right: 4px;"> ${displayRating} / 5`;
                if (this.clearBtn) this.clearBtn.style.display = 'inline-block';
            }
        }

        if (this.input) {
            this.input.value = value === 0 ? '' : value;
        }
        this.currentRating = value;
    }

    /**
     * Устанавливает новое значение рейтинга
     * @param {number} value - Новое значение (от 0 до 5)
     */
    setRating(value) {
        this.currentRating = Math.max(0, Math.min(5, value));
        this.updateStarsDisplay(this.currentRating);
    }

    /**
     * Возвращает текущее значение рейтинга
     * @returns {number} - Текущий рейтинг
     */
    getRating() {
        return this.currentRating;
    }
}

/**
 * Автоматическая инициализация для страниц с виджетом звёздного рейтинга
 */
document.addEventListener('DOMContentLoaded', () => {
    const ratingWidget = document.getElementById('starRatingWidget');
    if (ratingWidget) {
        const ratingInput = document.getElementById('ratingInput');
        const initialRating = ratingInput && ratingInput.value ? parseFloat(ratingInput.value) || 0 : 0;
        new StarRating('starRatingWidget', 'ratingInput', 'ratingText', 'clearRating', initialRating);
    }
});