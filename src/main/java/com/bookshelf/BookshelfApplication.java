package com.bookshelf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Главный класс приложения Bookshelf.
 * <p>
 * Этот класс служит точкой входа в Spring Boot приложение.
 * Он настраивает сканирование компонентов, поиск сущностей JPA и репозиториев.
 * </p>
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@SpringBootApplication(scanBasePackages = "com.bookshelf")
@EntityScan("com.bookshelf.model")
@EnableJpaRepositories("com.bookshelf.repository")
public class BookshelfApplication {

	/**
	 * Точка входа в приложение.
	 *
	 * @param args аргументы командной строки, передаваемые приложению
	 */
	public static void main(String[] args) {
		SpringApplication.run(BookshelfApplication.class, args);
	}
}