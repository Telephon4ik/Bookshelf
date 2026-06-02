/**
 * ISBN Scanner Component
 * Компонент для сканирования ISBN через камеру с использованием QuaggaJS
 *
 * @class ISBNScanner
 * @description Предоставляет функционал сканирования штрих-кодов ISBN с камеры устройства,
 *              валидации и отправки на сервер для поиска книги.
 */

/**
 * Конструктор сканера ISBN
 * @param {Object} options - Настройки сканера
 * @param {string} [options.defaultApi='openlibrary'] - API по умолчанию ('openlibrary' или 'google')
 * @param {Function} [options.onScanComplete] - Колбэк, вызываемый при успешном сканировании ISBN
 */
class ISBNScanner {
    constructor(options = {}) {
        /** @type {boolean} - Флаг работы сканера */
        this.scannerRunning = false;
        /** @type {MediaStream|null} - Текущий поток с камеры */
        this.currentStream = null;
        /** @type {string} - Выбранный API для поиска книг */
        this.selectedApi = options.defaultApi || 'openlibrary';
        /** @type {Function|null} - Колбэк завершения сканирования */
        this.onScanComplete = options.onScanComplete || null;

        /** @type {Object} - DOM элементы, используемые в компоненте */
        this.elements = {
            startBtn: document.getElementById('startScanner'),
            stopBtn: document.getElementById('stopScanner'),
            manualIsbn: document.getElementById('manualIsbn'),
            statusText: document.getElementById('statusText'),
            scanLine: document.getElementById('scanLine'),
            interactive: document.getElementById('interactive'),
            apiOpenLibrary: document.getElementById('apiOpenLibrary'),
            apiGoogle: document.getElementById('apiGoogle'),
            searchBtn: document.getElementById('searchBtn'),
            clearBtn: document.getElementById('clearBtn')
        };

        this.init();
    }

    /**
     * Инициализация компонента: привязка событий и установка API по умолчанию
     */
    init() {
        this.bindEvents();
        this.setSelectedApi(this.selectedApi);
    }

    /**
     * Привязывает обработчики событий к DOM элементам
     */
    bindEvents() {
        if (this.elements.startBtn) {
            this.elements.startBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.startScanner();
            });
        }

        if (this.elements.stopBtn) {
            this.elements.stopBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.stopScanner();
            });
        }

        if (this.elements.apiOpenLibrary) {
            this.elements.apiOpenLibrary.addEventListener('click', (e) => {
                e.preventDefault();
                this.setSelectedApi('openlibrary');
            });
        }

        if (this.elements.apiGoogle) {
            this.elements.apiGoogle.addEventListener('click', (e) => {
                e.preventDefault();
                this.setSelectedApi('google');
            });
        }

        if (this.elements.searchBtn) {
            this.elements.searchBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.searchByIsbn();
            });
        }

        if (this.elements.clearBtn) {
            this.elements.clearBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.clearInput();
            });
        }

        if (this.elements.manualIsbn) {
            this.elements.manualIsbn.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    this.searchByIsbn();
                }
            });
        }

        // Останавливаем камеру при уходе со страницы
        window.addEventListener('beforeunload', () => {
            if (this.scannerRunning) {
                this.forceStopScanner();
            }
        });
    }

    /**
     * Отображает статусное сообщение пользователю
     * @param {string} message - Текст сообщения
     * @param {boolean} [isError=false] - Флаг ошибки (меняет цвет текста)
     */
    showStatus(message, isError = false) {
        if (this.elements.statusText) {
            this.elements.statusText.innerText = message;
            if (isError) {
                this.elements.statusText.style.color = '#D84315';
                setTimeout(() => {
                    if (this.elements.statusText) {
                        this.elements.statusText.style.color = '';
                    }
                }, 3000);
            } else {
                this.elements.statusText.style.color = '';
            }
        }
    }

    /**
     * Устанавливает выбранный API для поиска книг
     * @param {string} api - 'openlibrary' или 'google'
     */
    setSelectedApi(api) {
        this.selectedApi = api;

        if (this.elements.apiOpenLibrary && this.elements.apiGoogle) {
            if (api === 'openlibrary') {
                this.elements.apiOpenLibrary.classList.add('active');
                this.elements.apiGoogle.classList.remove('active');
            } else {
                this.elements.apiOpenLibrary.classList.remove('active');
                this.elements.apiGoogle.classList.add('active');
            }
        }

        this.showStatus(api === 'openlibrary' ? 'Выбран Open Library' : 'Выбран Google Books');
        setTimeout(() => {
            const status = this.elements.statusText?.innerText;
            if (status === 'Выбран Open Library' || status === 'Выбран Google Books') {
                if (this.elements.statusText) {
                    this.elements.statusText.innerText = '';
                }
            }
        }, 1500);
    }

    /**
     * Валидирует ISBN на соответствие формату ISBN-10 или ISBN-13
     * @param {string} isbn - Строка для проверки
     * @returns {boolean} - true, если ISBN валиден
     */
    validateIsbn(isbn) {
        if (!isbn) return false;

        let cleanIsbn = isbn.replace(/[^0-9X]/gi, '');

        if (cleanIsbn.length === 10) {
            return cleanIsbn.match(/^\d{9}[\dX]$/i) !== null;
        } else if (cleanIsbn.length === 13) {
            return cleanIsbn.match(/^\d{13}$/) !== null;
        }

        return false;
    }

    /**
     * Очищает ISBN от нецифровых символов и приводит к верхнему регистру
     * @param {string} isbn - Исходный ISBN
     * @returns {string} - Очищенный ISBN
     */
    cleanIsbn(isbn) {
        if (!isbn) return '';
        return isbn.replace(/[^0-9X]/gi, '').toUpperCase();
    }

    /**
     * Выполняет поиск книги по ISBN (отправка формы на сервер)
     */
    searchByIsbn() {
        const isbnInput = this.elements.manualIsbn;
        let isbn = isbnInput ? isbnInput.value.trim() : '';

        if (!isbn) {
            this.showStatus('❌ Введите ISBN или отсканируйте штрих-код', true);
            if (isbnInput) {
                isbnInput.style.borderColor = '#D84315';
                setTimeout(() => {
                    if (isbnInput) isbnInput.style.borderColor = '';
                }, 2000);
            }
            return;
        }

        let cleanIsbn = this.cleanIsbn(isbn);

        if (!this.validateIsbn(cleanIsbn)) {
            this.showStatus('❌ Некорректный ISBN. Введите 10 или 13 цифр.', true);
            if (isbnInput) {
                isbnInput.style.borderColor = '#D84315';
                setTimeout(() => {
                    if (isbnInput) isbnInput.style.borderColor = '';
                }, 2000);
            }
            return;
        }

        let form = document.createElement('form');
        form.method = 'POST';
        form.action = '/scan-isbn';

        let isbnField = document.createElement('input');
        isbnField.type = 'hidden';
        isbnField.name = 'isbn';
        isbnField.value = cleanIsbn;
        form.appendChild(isbnField);

        let apiField = document.createElement('input');
        apiField.type = 'hidden';
        apiField.name = 'apiSource';
        apiField.value = this.selectedApi;
        form.appendChild(apiField);

        document.body.appendChild(form);
        form.submit();
    }

    /**
     * Очищает поле ввода ISBN
     */
    clearInput() {
        if (this.elements.manualIsbn) {
            this.elements.manualIsbn.value = '';
            this.elements.manualIsbn.style.borderColor = '';
            this.elements.manualIsbn.style.backgroundColor = '';
        }
        this.showStatus('Поле очищено');
    }

    /**
     * Подсвечивает успешно отсканированный ISBN в поле ввода
     * @param {string} isbn - Отсканированный ISBN
     */
    highlightScannedIsbn(isbn) {
        if (this.elements.manualIsbn) {
            this.elements.manualIsbn.value = isbn;
            this.elements.manualIsbn.style.borderColor = '#4CAF50';
            this.elements.manualIsbn.style.backgroundColor = '#E8F5E9';

            setTimeout(() => {
                if (this.elements.manualIsbn) {
                    this.elements.manualIsbn.style.borderColor = '';
                    this.elements.manualIsbn.style.backgroundColor = '';
                }
            }, 2000);
        }
    }

    /**
     * Запускает сканер камеры с использованием QuaggaJS
     */
    startScanner() {
        if (this.scannerRunning) {
            this.showStatus('Сканер уже запущен');
            return;
        }

        if (typeof Quagga === 'undefined') {
            this.showStatus('❌ Библиотека сканирования не загружена. Обновите страницу.', true);
            return;
        }

        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            this.showStatus('❌ Ваш браузер не поддерживает доступ к камере.', true);
            return;
        }

        this.showStatus('Запуск камеры...');

        if (this.elements.interactive) {
            while (this.elements.interactive.firstChild) {
                this.elements.interactive.removeChild(this.elements.interactive.firstChild);
            }
        }

        const config = {
            inputStream: {
                name: "Live",
                type: "LiveStream",
                target: this.elements.interactive,
                constraints: {
                    facingMode: "environment",
                    width: { min: 640, ideal: 1280 },
                    height: { min: 480, ideal: 720 }
                }
            },
            locator: {
                patchSize: "medium",
                halfSample: true
            },
            numOfWorkers: 0,
            frequency: 10,
            decoder: {
                readers: ["ean_reader", "ean_8_reader", "upc_reader", "upc_e_reader"]
            },
            debug: false
        };

        try {
            const self = this;
            Quagga.init(config, function(err) {
                if (err) {
                    self.showStatus('❌ Ошибка запуска камеры: ' + (err.message || 'неизвестная ошибка'), true);
                    return;
                }

                Quagga.start();
                self.scannerRunning = true;

                setTimeout(() => {
                    const video = document.querySelector('#interactive video');
                    if (video && video.srcObject) {
                        self.currentStream = video.srcObject;
                    }
                }, 500);

                if (self.elements.startBtn) self.elements.startBtn.disabled = true;
                if (self.elements.stopBtn) self.elements.stopBtn.disabled = false;

                if (self.elements.scanLine) {
                    self.elements.scanLine.style.display = 'block';
                }

                self.showStatus('✅ Камера активна. Наведите на штрих-код...');

                Quagga.onDetected(function(result) {
                    if (!self.scannerRunning) return;

                    let code = result.codeResult.code;

                    if (code && code.length >= 8) {
                        let cleanCode = code.replace(/[^0-9X]/gi, '');

                        if (cleanCode.length === 10 || cleanCode.length === 13) {
                            self.stopScanner();
                            self.highlightScannedIsbn(cleanCode);
                            self.showStatus('✅ ISBN отсканирован! Нажмите "Найти книгу" для поиска');

                            if (self.onScanComplete) {
                                self.onScanComplete(cleanCode);
                            }
                        }
                    }
                });
            });
        } catch(e) {
            this.showStatus('❌ Ошибка: ' + e.message, true);
            if (this.elements.startBtn) this.elements.startBtn.disabled = false;
        }
    }

    /**
     * Останавливает сканер камеры
     */
    stopScanner() {
        if (!this.scannerRunning) return;

        try {
            if (typeof Quagga !== 'undefined' && Quagga) {
                Quagga.stop();
            }

            if (this.currentStream) {
                this.currentStream.getTracks().forEach(track => {
                    track.stop();
                });
                this.currentStream = null;
            }

            if (this.elements.interactive) {
                while (this.elements.interactive.firstChild) {
                    this.elements.interactive.removeChild(this.elements.interactive.firstChild);
                }
            }

            this.scannerRunning = false;

            if (this.elements.startBtn) this.elements.startBtn.disabled = false;
            if (this.elements.stopBtn) this.elements.stopBtn.disabled = true;

            if (this.elements.scanLine) {
                this.elements.scanLine.style.display = 'none';
            }

            this.showStatus('Камера остановлена');

        } catch(e) {
            this.showStatus('Ошибка при остановке', true);
        }
    }

    /**
     * Принудительно останавливает сканер (используется при уходе со страницы)
     */
    forceStopScanner() {
        try {
            if (typeof Quagga !== 'undefined') Quagga.stop();
            if (this.currentStream) {
                this.currentStream.getTracks().forEach(track => track.stop());
            }
        } catch(e) {}
    }

    /**
     * Проверяет, запущен ли сканер
     * @returns {boolean} - true, если сканер активен
     */
    isRunning() {
        return this.scannerRunning;
    }

    /**
     * Возвращает выбранный API
     * @returns {string} - 'openlibrary' или 'google'
     */
    getSelectedApi() {
        return this.selectedApi;
    }
}

/**
 * Автоматическая инициализация при загрузке страницы
 */
document.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('scannerContainer')) {
        window.isbnScanner = new ISBNScanner({
            defaultApi: 'openlibrary',
            onScanComplete: (isbn) => {
                console.log('ISBN scanned:', isbn);
            }
        });
    }
});