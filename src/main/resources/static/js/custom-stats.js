/**
 * Статистика чтения с графиками
 * Отображает количество прочитанных книг по месяцам и распределение по жанрам
 */

class ReadingStatsChart {
    constructor() {
        this.chart = null;
        this.pieChart = null;
        this.currentYear = new Date().getFullYear();
        this.availableYears = [];
        this.genreData = null;

        this.elements = {
            yearSelect: document.getElementById('yearSelect'),
            prevYearBtn: document.getElementById('prevYear'),
            nextYearBtn: document.getElementById('nextYear'),
            totalBooksYear: document.getElementById('totalBooksYear'),
            avgPerMonth: document.getElementById('avgPerMonth'),
            bestMonth: document.getElementById('bestMonth'),
            toggleLegendBtn: document.getElementById('toggleLegend'),
            pieLegend: document.getElementById('pieLegend'),
            totalGenres: document.getElementById('totalGenres'),
            totalGenreBooks: document.getElementById('totalGenreBooks')
        };

        this.monthNames = [
            'Янв', 'Фев', 'Мар', 'Апр', 'Май', 'Июн',
            'Июл', 'Авг', 'Сен', 'Окт', 'Ноя', 'Дек'
        ];

        // Цветовая палитра для жанров
        this.colorPalette = [
            '#8B5A2B', '#D4A373', '#B5835A', '#E6B17E', '#C38D5D',
            '#A0714F', '#E8B87A', '#9B6A3C', '#F0C48F', '#7A5230',
            '#CD8D58', '#B5733A', '#E2A96B', '#8C5F35', '#DCA06C',
            '#A67C52', '#F4BB7A', '#94683E', '#EAB47F', '#806040'
        ];

        this.init();
    }

    async init() {
        // Получаем доступные годы с сервера
        await this.loadAvailableYears();

        // Загружаем данные по жанрам
        await this.loadGenreData();

        // Привязываем события
        this.bindEvents();

        // Загружаем данные для текущего года
        await this.loadData(this.currentYear);
    }

    async loadGenreData() {
        try {
            const response = await fetch('/stats/genre-data');
            const data = await response.json();

            if (data.error) {
                console.error('Ошибка загрузки данных жанров:', data.error);
                return;
            }

            this.genreData = data;
            this.renderPieChart(data.labels, data.values);
            this.updateGenreStats(data);

        } catch (error) {
            console.error('Ошибка загрузки данных жанров:', error);
        }
    }

    renderPieChart(labels, values) {
        const ctx = document.getElementById('genrePieChart');
        if (!ctx) return;

        if (this.pieChart) {
            this.pieChart.destroy();
        }

        // Подготавливаем цвета
        const backgroundColors = labels.map((_, index) =>
            this.colorPalette[index % this.colorPalette.length]
        );

        this.pieChart = new Chart(ctx, {
            type: 'pie',
            data: {
                labels: labels,
                datasets: [{
                    data: values,
                    backgroundColor: backgroundColors,
                    borderColor: '#FFFFFF',
                    borderWidth: 2,
                    hoverOffset: 15
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false // Отключаем встроенную легенду, используем кастомную
                    },
                    tooltip: {
                        backgroundColor: '#FFFFFF',
                        titleColor: '#3E2E22',
                        bodyColor: '#5C4B3A',
                        borderColor: '#E8DDD0',
                        borderWidth: 1,
                        padding: 10,
                        cornerRadius: 8,
                        callbacks: {
                            label: function(context) {
                                const label = context.label;
                                const value = context.raw;
                                const total = context.dataset.data.reduce((a, b) => a + b, 0);
                                const percentage = ((value / total) * 100).toFixed(1);
                                const booksText = this.getBooksDeclension(value);
                                return `${label}: ${value} ${booksText} (${percentage}%)`;
                            }
                        }
                    }
                },
                layout: {
                    padding: 10
                },
                animation: {
                    duration: 750,
                    easing: 'easeOutQuart'
                }
            }
        });

        // Создаем кастомную легенду
        this.createCustomLegend(labels, values);
    }

    getBooksDeclension(count) {
        if (count === 1) return 'книга';
        if (count >= 2 && count <= 4) return 'книги';
        return 'книг';
    }

    createCustomLegend(labels, values) {
        if (!this.elements.pieLegend) return;

        const total = values.reduce((a, b) => a + b, 0);

        this.elements.pieLegend.innerHTML = '';

        labels.forEach((label, index) => {
            const value = values[index];
            const percentage = ((value / total) * 100).toFixed(1);
            const color = this.colorPalette[index % this.colorPalette.length];

            const legendItem = document.createElement('div');
            legendItem.className = 'pie-legend-item';
            legendItem.setAttribute('data-index', index);

            legendItem.innerHTML = `
                <div class="pie-legend-color" style="background-color: ${color};"></div>
                <div class="pie-legend-label" title="${label}">${this.truncateLabel(label, 20)}</div>
                <div class="pie-legend-value">${value} (${percentage}%)</div>
            `;

            // Добавляем эффект подсветки при наведении
            legendItem.addEventListener('mouseenter', () => {
                if (this.pieChart) {
                    this.pieChart.setDatasetVisibility(0, true);
                    this.pieChart.toggleDataVisibility(index);
                    this.pieChart.update();
                    setTimeout(() => {
                        if (this.pieChart) {
                            this.pieChart.toggleDataVisibility(index);
                            this.pieChart.update();
                        }
                    }, 1000);
                }
            });

            this.elements.pieLegend.appendChild(legendItem);
        });
    }

    truncateLabel(label, maxLength) {
        if (label.length <= maxLength) return label;
        return label.substring(0, maxLength - 3) + '...';
    }

    updateGenreStats(data) {
        if (this.elements.totalGenres) {
            this.elements.totalGenres.textContent = data.labels.length;
        }
        if (this.elements.totalGenreBooks) {
            this.elements.totalGenreBooks.textContent = data.total;
        }
    }

    toggleLegend() {
        if (this.elements.pieLegend) {
            this.elements.pieLegend.classList.toggle('visible');
        }
    }

    async loadAvailableYears() {
        try {
            const response = await fetch('/stats/monthly-data?year=' + this.currentYear);
            const data = await response.json();

            if (data.availableYears) {
                this.availableYears = data.availableYears;
                this.currentYear = data.year || this.currentYear;

                // Обновляем выпадающий список
                if (this.elements.yearSelect) {
                    this.elements.yearSelect.innerHTML = '';
                    this.availableYears.forEach(year => {
                        const option = document.createElement('option');
                        option.value = year;
                        option.textContent = year;
                        if (year === this.currentYear) {
                            option.selected = true;
                        }
                        this.elements.yearSelect.appendChild(option);
                    });
                }
            }
        } catch (error) {
            console.error('Ошибка загрузки доступных годов:', error);
        }
    }

    bindEvents() {
        if (this.elements.yearSelect) {
            this.elements.yearSelect.addEventListener('change', (e) => {
                this.currentYear = parseInt(e.target.value);
                this.loadData(this.currentYear);
            });
        }

        if (this.elements.prevYearBtn) {
            this.elements.prevYearBtn.addEventListener('click', () => {
                const minYear = Math.min(...this.availableYears);
                if (this.currentYear > minYear) {
                    this.currentYear--;
                    this.updateYearSelect(this.currentYear);
                    this.loadData(this.currentYear);
                }
            });
        }

        if (this.elements.nextYearBtn) {
            this.elements.nextYearBtn.addEventListener('click', () => {
                const maxYear = Math.max(...this.availableYears);
                if (this.currentYear < maxYear) {
                    this.currentYear++;
                    this.updateYearSelect(this.currentYear);
                    this.loadData(this.currentYear);
                }
            });
        }

        if (this.elements.toggleLegendBtn) {
            this.elements.toggleLegendBtn.addEventListener('click', () => {
                this.toggleLegend();
            });
        }
    }

    updateYearSelect(year) {
        if (this.elements.yearSelect) {
            this.elements.yearSelect.value = year;
        }
        this.currentYear = year;
    }

    async loadData(year) {
        try {
            const response = await fetch(`/stats/monthly-data?year=${year}`);
            const data = await response.json();

            if (data.error) {
                console.error('Ошибка:', data.error);
                return;
            }

            this.renderChart(data.months, data.values);
            this.updateStats(data.values);

        } catch (error) {
            console.error('Ошибка загрузки данных:', error);
        }
    }

    renderChart(months, values) {
        const ctx = document.getElementById('monthlyChart');
        if (!ctx) return;

        const labels = months.map(m => this.monthNames[m - 1]);

        if (this.chart) {
            this.chart.destroy();
        }

        this.chart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Завершено книг',
                    data: values,
                    backgroundColor: 'rgba(139, 90, 43, 0.7)',
                    borderColor: '#8B5A2B',
                    borderWidth: 2,
                    borderRadius: 8,
                    barPercentage: 0.7,
                    categoryPercentage: 0.8
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: true,
                        position: 'top',
                        labels: {
                            font: {
                                family: "'Inter', sans-serif",
                                size: 12
                            },
                            color: '#5C4B3A',
                            usePointStyle: true,
                            pointStyle: 'rectRounded'
                        }
                    },
                    tooltip: {
                        backgroundColor: '#FFFFFF',
                        titleColor: '#3E2E22',
                        bodyColor: '#5C4B3A',
                        borderColor: '#E8DDD0',
                        borderWidth: 1,
                        padding: 10,
                        cornerRadius: 8,
                        callbacks: {
                            label: function(context) {
                                const value = context.raw;
                                const label = context.dataset.label;
                                if (value === 0) {
                                    return `${label}: 0 книг`;
                                } else if (value === 1) {
                                    return `${label}: ${value} книга`;
                                } else if (value >= 2 && value <= 4) {
                                    return `${label}: ${value} книги`;
                                } else {
                                    return `${label}: ${value} книг`;
                                }
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        grid: {
                            color: '#E8DDD0',
                            drawBorder: false
                        },
                        ticks: {
                            stepSize: 1,
                            precision: 0,
                            color: '#7A6A5A',
                            font: {
                                family: "'Inter', sans-serif",
                                size: 11
                            }
                        },
                        title: {
                            display: true,
                            text: 'Количество книг',
                            color: '#7A6A5A',
                            font: {
                                family: "'Inter', sans-serif",
                                size: 12,
                                weight: 500
                            }
                        }
                    },
                    x: {
                        grid: {
                            display: false
                        },
                        ticks: {
                            color: '#7A6A5A',
                            font: {
                                family: "'Inter', sans-serif",
                                size: 11
                            }
                        }
                    }
                },
                layout: {
                    padding: {
                        left: 10,
                        right: 10,
                        top: 20,
                        bottom: 10
                    }
                },
                animation: {
                    duration: 750,
                    easing: 'easeInOutQuart'
                }
            }
        });
    }

    updateStats(values) {
        const total = values.reduce((sum, val) => sum + val, 0);
        const avg = values.length > 0 ? (total / values.length).toFixed(1) : 0;

        let maxValue = 0;
        let bestMonthIndex = -1;

        for (let i = 0; i < values.length; i++) {
            if (values[i] > maxValue) {
                maxValue = values[i];
                bestMonthIndex = i;
            }
        }

        const bestMonthName = bestMonthIndex !== -1 ? this.monthNames[bestMonthIndex] : '—';
        const bestMonthValue = maxValue;

        if (this.elements.totalBooksYear) {
            this.elements.totalBooksYear.textContent = total;
        }

        if (this.elements.avgPerMonth) {
            this.elements.avgPerMonth.textContent = avg + ' кн.';
        }

        if (this.elements.bestMonth) {
            if (bestMonthValue === 0) {
                this.elements.bestMonth.textContent = '—';
            } else if (bestMonthValue === 1) {
                this.elements.bestMonth.textContent = `${bestMonthName} (${bestMonthValue} книга)`;
            } else if (bestMonthValue >= 2 && bestMonthValue <= 4) {
                this.elements.bestMonth.textContent = `${bestMonthName} (${bestMonthValue} книги)`;
            } else {
                this.elements.bestMonth.textContent = `${bestMonthName} (${bestMonthValue} книг)`;
            }
        }
    }
}

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('monthlyChart')) {
        window.readingStatsChart = new ReadingStatsChart();
    }
});