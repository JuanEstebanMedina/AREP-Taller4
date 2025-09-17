package co.escuelaing.arep.microspringboot.examples;

import co.escuelaing.arep.microspringboot.annotations.RestController;
import co.escuelaing.arep.microspringboot.annotations.GetMapping;
import co.escuelaing.arep.microspringboot.annotations.RequestParam;

/**
 *
 * @author juan.medina-r
 */
@RestController
public class GreetingController {

	private static final String TEMPLATE = "Hello, %s!";

	private GreetingController() {
		throw new IllegalStateException("Utility class");
	}

	@GetMapping("/greeting")
	public static String greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
		return String.format(TEMPLATE, name);
	}
}