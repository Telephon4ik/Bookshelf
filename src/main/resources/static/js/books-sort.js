/**
 * Компонент выпадающего списка сортировки для страницы книг
 * Открывается ТОЛЬКО при клике, закрывается при клике вне
 *
 * @class BooksSortDropdown
 * @description Обеспечивает функционал выпадающего меню сортировки книг
 */
class BooksSortDropdown {
    /**
     * Конструктор компонента сортировки
     */
    constructor() {
        /** @type {HTMLElement|null} - Контейнер сортировки */
        this.sortWrapper = document.getElementById('sortWrapper');
        /** @type {HTMLElement|null} - Кнопка-триггер */
        this.sortTrigger = document.getElementById('sortTrigger');
        /** @type {HTMLElement|null} - Выпадающий список */
        this.sortDropdown = document.getElementById('sortDropdown');
        /** @type {boolean} - Флаг инициализации */
        this.initialized = false;

        this.init();
    }

    /**
     * Инициализация компонента: привязка событий
     */
    init() {
        if (!this.sortWrapper || !this.sortTrigger || !this.sortDropdown) {
            console.warn('BooksSortDropdown: необходимые элементы не найдены');
            return;
        }

        // Предотвращаем повторную инициализацию
        if (this.sortWrapper.hasAttribute('data-sort-initialized')) return;
        this.sortWrapper.setAttribute('data-sort-initialized', 'true');
        this.initialized = true;

        this.bindEvents();
    }

    /**
     * Привязывает обработчики событий
     */
    bindEvents() {
        // Закрытие при клике вне компонента
        document.addEventListener('click', (e) => {
            if (!this.sortWrapper.contains(e.target)) {
                this.closeDropdown();
            }
        });

        // Открытие/закрытие при клике на триггер
        this.sortTrigger.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            this.toggleDropdown();
        });
    }

    /**
     * Открывает выпадающий список
     */
    openDropdown() {
        // Закрываем все другие открытые дропдауны
        document.querySelectorAll('.sort-wrapper.open').forEach(sw => {
            if (sw !== this.sortWrapper) {
                sw.classList.remove('open');
            }
        });
        this.sortWrapper.classList.add('open');
    }

    /**
     * Закрывает выпадающий список
     */
    closeDropdown() {
        this.sortWrapper.classList.remove('open');
    }

    /**
     * Переключает состояние выпадающего списка
     */
    toggleDropdown() {
        if (this.sortWrapper.classList.contains('open')) {
            this.closeDropdown();
        } else {
            this.openDropdown();
        }
    }

    /**
     * Проверяет, открыт ли список
     * @returns {boolean}
     */
    isOpen() {
        return this.sortWrapper.classList.contains('open');
    }
}

/**
 * Автоматическая инициализация при загрузке страницы
 */
document.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('sortWrapper')) {
        window.booksSortDropdown = new BooksSortDropdown();
    }
});