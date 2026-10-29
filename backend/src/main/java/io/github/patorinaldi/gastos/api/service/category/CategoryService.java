package io.github.patorinaldi.gastos.api.service.category;

import io.github.patorinaldi.gastos.api.domain.Category;
import io.github.patorinaldi.gastos.api.repository.CategoryRepository;
import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M4, catálogo de categorías del hogar (RF-24): alta, renombrado y baja lógica.
 *
 * <p>{@code categories} está bajo RLS, así que ningún método recibe ni consulta el hogar: una categoría
 * de otro hogar no se encuentra y responde como una inexistente. El hogar del usuario solo se usa para
 * insertar, donde la política exige que coincida con el de la sesión.
 *
 * <p>El nombre es único entre las categorías activas del hogar, sin distinguir mayúsculas (RN-09). Se
 * comprueba antes para responder 409 sin intentar el insert, pero la comprobación sola no alcanza: dos
 * pedidos simultáneos con el mismo nombre la pasan los dos, y ahí decide el índice
 * {@value #NAME_INDEX}. Por eso el alta y el renombrado corren en una transacción propia y la violación
 * del índice se reconoce afuera, como en {@code RegistrationService}.
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    static final String NAME_INDEX = "categories_household_name_key";

    private final CategoryRepository categories;
    private final TransactionTemplate transactions;

    public CategoryService(CategoryRepository categories, PlatformTransactionManager transactionManager) {
        this.categories = categories;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** El catálogo que se ofrece: las activas, por nombre. */
    @Transactional(readOnly = true)
    public List<CategoryView> list() {
        return categories.findAllByActiveTrueOrderByNameAsc().stream().map(CategoryService::view).toList();
    }

    /**
     * Crea una categoría. El nombre llega validado como visible; se guarda sin los espacios de los
     * extremos.
     *
     * @throws CategoryNameTakenException si ya hay una activa con ese nombre (RN-09)
     */
    public CategoryView create(AuthenticatedUser user, String name) {
        String stripped = name.strip();
        return uniqueName(() -> {
            if (categories.findByNameIgnoreCaseAndActiveTrue(stripped).isPresent()) {
                throw new CategoryNameTakenException();
            }
            Category created = categories.saveAndFlush(new Category(user.householdId(), stripped));
            log.info("Categoría {} creada por el usuario {} en el hogar {}", created.getId(), user.userId(), user.householdId());
            return view(created);
        });
    }

    /**
     * Renombra una categoría activa. El nombre nuevo se ve en todos sus gastos, también en los de
     * períodos anteriores, y sus reglas siguen aplicándose (RN-16). Cambiar solo las mayúsculas del
     * propio nombre está permitido.
     *
     * @throws CategoryNotFoundException  si no existe, está dada de baja o es de otro hogar
     * @throws CategoryNameTakenException si otra categoría activa ya tiene ese nombre (RN-09)
     */
    public CategoryView rename(AuthenticatedUser user, UUID id, String name) {
        String stripped = name.strip();
        return uniqueName(() -> {
            Category category = categories.findByIdAndActiveTrue(id).orElseThrow(CategoryNotFoundException::new);
            categories.findByNameIgnoreCaseAndActiveTrue(stripped)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new CategoryNameTakenException();
                    });
            category.setName(stripped);
            categories.flush();
            log.info("Categoría {} renombrada por el usuario {}", id, user.userId());
            return view(category);
        });
    }

    /**
     * Baja lógica (RN-16). La categoría deja de ofrecerse y el trigger de la V4 elimina sus reglas, así
     * que deja de asignarse a gastos nuevos. Los gastos que ya la tenían la conservan, en el historial y
     * en el análisis. Su nombre queda libre para otra categoría.
     *
     * @throws CategoryNotFoundException si no existe, ya está dada de baja o es de otro hogar
     */
    @Transactional
    public void deactivate(AuthenticatedUser user, UUID id) {
        Category category = categories.findByIdAndActiveTrue(id).orElseThrow(CategoryNotFoundException::new);
        categories.delete(category);
        log.info("Categoría {} dada de baja por el usuario {}", id, user.userId());
    }

    private <T> T uniqueName(Supplier<T> work) {
        try {
            return transactions.execute(status -> work.get());
        } catch (DataIntegrityViolationException ex) {
            if (UniqueViolations.of(ex, NAME_INDEX)) {
                throw new CategoryNameTakenException();
            }
            throw ex;
        }
    }

    private static CategoryView view(Category category) {
        return new CategoryView(category.getId(), category.getName());
    }
}
