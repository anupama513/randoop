package randoop.operation;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import randoop.reflection.DefaultReflectionPredicate;
import randoop.reflection.ModelCollections;
import randoop.reflection.OperationExtractor;
import randoop.reflection.PublicVisibilityPredicate;
import randoop.reflection.ReflectionManager;
import randoop.reflection.ReflectionPredicate;
import randoop.reflection.TypedOperationManager;
import randoop.reflection.VisibilityPredicate;
import randoop.test.ClassWithInnerEnum;
import randoop.test.Coin;
import randoop.test.EnumAsPredicate;
import randoop.test.OperatorEnum;
import randoop.test.PlayingCard;
import randoop.test.SimpleEnum;
import randoop.types.ClassOrInterfaceType;
import randoop.types.GeneralType;
import randoop.types.RandoopTypeException;
import randoop.types.SimpleClassOrInterfaceType;
import randoop.types.TypeTuple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * EnumReflectionTest consists of tests of reflection classes
 * to verify what is collected from enums and classes using enums. In particular,
 * want to collect enum constants, methods of enum (esp. if abstract), enums that are
 * are inner types.
 *
 */
public class EnumReflectionTest {

  /**
   * simpleEnum tests that for a simple enum (constants and no explicit
   * methods) that we just get the constant valuse and no methods.
   * Uses randoop.test.SimpleEnum, which is a basic enum
   * with four values: ONE, TWO, THREE, and FOUR
   */
  @Test
  public void simpleEnum() {
    Class<?> se = SimpleEnum.class;
    ClassOrInterfaceType declaringType = new SimpleClassOrInterfaceType(se);

    @SuppressWarnings("unchecked")
    List<Enum<?>> include = asList(se.getEnumConstants());
    @SuppressWarnings("unchecked")
    List<Method> exclude = Arrays.asList(se.getMethods());
    Set<TypedOperation> actual = getConcreteOperations(se);

    assertEquals("number of statements", include.size(), actual.size());

    for (Enum<?> e : include) {
      assertTrue(
          "enum constant " + e.name() + " should occur", actual.contains(createEnumOperation(e)));
    }
    for (Method m : exclude) {
      try {
        assertFalse(
            "method " + m.toGenericString() + " should not occur in simple enum",
            actual.contains(createMethodCall(m, declaringType)));
      } catch (RandoopTypeException e) {
        fail("type error: " + e);
      }
    }
  }

  private List<Enum<?>> asList(Object[] enumConstants) {
    List<Enum<?>> list = new ArrayList<>();
    for (Object obj : enumConstants) {
      if (obj instanceof Enum) {
        list.add((Enum<?>) obj);
      }
    }
    return list;
  }

  /**
   * innerEnum tests that Reflection.getStatements is collecting simple
   * enum constants from a class. Uses randoop.test.PlayingCard, which has
   * two public enums, one private enum, and one enum with package access.
   *
   */
  @SuppressWarnings("unchecked")
  @Test
  public void innerEnum() {
    Class<?> pc = PlayingCard.class;

    List<Enum<?>> include = new ArrayList<>();
    List<Enum<?>> exclude = new ArrayList<>();
    for (Class<?> c : pc.getDeclaredClasses()) {
      int mods = c.getModifiers();
      if (c.isEnum()) {
        if (Modifier.isPublic(mods)) {
          include.addAll(asList(c.getEnumConstants()));
        } else {
          exclude.addAll(asList(c.getEnumConstants()));
        }
      }
    }

    Set<TypedOperation> actual = getConcreteOperations(pc);
    assertEquals("number of statements", include.size() + 5, actual.size());

    for (Enum<?> e : include) {
      assertTrue(
          "enum constant " + e.name() + " should occur", actual.contains(createEnumOperation(e)));
    }
    for (Enum<?> e : exclude) {
      assertFalse(
          "enum constant " + e.name() + " should not occur", actual.contains(createEnumOperation(e)));
    }
  }

  @Test
  public void innerEnumWithMethodsTest() {
    Class<?> c = ClassWithInnerEnum.class;
    Class<?>[] memberClasses = c.getDeclaredClasses();

    // TODO test that declaring class of operations for inner enum is enum

    Set<TypedOperation> actual = getConcreteOperations(c);

    assertEquals("number of statments", 13, actual.size());
  }

  @Test
  public void enumAsPredicateTest() {
    Class<?> c = EnumAsPredicate.class;
    Set<TypedOperation> actual = getConcreteOperations(c);
    // TODO this should be 5, except for odd business with lost type of inherited method
    assertEquals("number of operations", 4, actual.size());
  }
  

  /**
   * valueEnum tests Reflection.getStatements for an enum with a field.
   * Uses randoop.test.Coin, which has a private int field, a private constructor,
   * and one public accessor method. Expect that should return the constants and accessor.
   * Note that compiler ensures constructor access is at least package level.
   */
  @Test
  public void valueEnum() {
    Class<?> coin = Coin.class;
    ClassOrInterfaceType declaringType = new SimpleClassOrInterfaceType(coin);

    Set<TypedOperation> actual = getConcreteOperations(coin);

    int count = 0;
    for (Object obj : coin.getEnumConstants()) {
      Enum<?> e = (Enum<?>) obj;
      assertTrue(
          "enum constant " + e.name() + " should occur", actual.contains(createEnumOperation(e)));
      count++;
    }

    for (Constructor<?> con : coin.getDeclaredConstructors()) {
      try {
        assertFalse(
            "enum constructor " + con.getName() + "should not occur",
            actual.contains(createConstructorCall(con)));
      } catch (RandoopTypeException e) {
        fail("type error: " + e);
      }
    }

    for (Method m : coin.getMethods()) {
      TypedOperation mc = null;
      try {
        mc = createMethodCall(m, declaringType);
      } catch (RandoopTypeException e) {
        fail("type error: " + e);
      }
      if (m.getName().equals("value")) {
        assertTrue(
            "enum method " + m.toGenericString() + " should occur",
            actual.contains(mc));
        count++;
      } else {
        assertFalse(
            "enum method " + m.toGenericString() + " should not occur",
            actual.contains(mc));
      }
    }
    assertEquals("number of statements", count, actual.size());
  }

 

  /**
   * abstractMethodEnum tests Reflection.getStatements for an enum with an abstract method
   * and overridden Object methods.
   * Uses randoop.test.Operator that has four constants, one abstract method eval and each
   * constant implements eval and toString.
   */
  @Test
  public void abstractMethodEnum() {
    Class<?> op = OperatorEnum.class;
    ClassOrInterfaceType declaringType = new SimpleClassOrInterfaceType(op);

    Set<TypedOperation> actual = getConcreteOperations(op);
    Set<String> overrides = new TreeSet<>();
    int count = 0;
    for (Object obj : op.getEnumConstants()) {
      Enum<?> e = (Enum<?>) obj;
      assertTrue(
          "enum constant " + e.name() + " should occur", actual.contains(createEnumOperation(e)));
      count++;
      for (Method m : e.getClass().getDeclaredMethods()) {
        overrides.add(m.getName());
      }
    }

    for (Method m : op.getMethods()) {
      TypedOperation mc = null;
      try {
        mc = createMethodCall(m, declaringType);
      } catch (RandoopTypeException e) {
        fail("type error: " + e.getMessage());
      }
      if (overrides.contains(m.getName())) {
        assertTrue(
            "enum method " + m.toGenericString() + " should occur",
            actual.contains(mc));
        count++;
      } else {
        assertFalse(
            "enum method " + m.toGenericString() + " should not occur",
            actual.contains(mc));
      }
    }

    assertEquals("number of operations", count, actual.size());
  }

  private Set<TypedOperation> getConcreteOperations(Class<?> c) {
    return getConcreteOperations(c, new DefaultReflectionPredicate(), new PublicVisibilityPredicate());
  }

  private Set<TypedOperation> getConcreteOperations(Class<?> c, ReflectionPredicate predicate, VisibilityPredicate visibilityPredicate) {
    final Set<TypedOperation> operations = new LinkedHashSet<>();
    TypedOperationManager operationManager = new TypedOperationManager(new ModelCollections() {
      @Override
      public void addConcreteOperation(ClassOrInterfaceType declaringType, TypedOperation operation) {
        operations.add(operation);
      }
    });
    OperationExtractor extractor = new OperationExtractor(operationManager, predicate);
    ReflectionManager manager = new ReflectionManager(visibilityPredicate);
    manager.add(extractor);
    manager.apply(c);
    return operations;
  }

  private TypedOperation createEnumOperation(Enum<?> e) {
    CallableOperation eOp = new EnumConstant(e);
    ClassOrInterfaceType enumType = new SimpleClassOrInterfaceType(e.getDeclaringClass());
    return new TypedClassOperation(eOp, enumType, new TypeTuple(), enumType);
  }

  private TypedOperation createConstructorCall(Constructor<?> con) throws RandoopTypeException {
    ConstructorCall op = new ConstructorCall(con);
    ClassOrInterfaceType declaringType = ClassOrInterfaceType.forClass(con.getDeclaringClass());
    List<GeneralType> paramTypes = new ArrayList<>();
    for (Class<?> pc : con.getParameterTypes()) {
      paramTypes.add(GeneralType.forClass(pc));
    }
    return new TypedClassOperation(op, declaringType, new TypeTuple(paramTypes), declaringType);
  }
  
  private TypedOperation createMethodCall(Method m, ClassOrInterfaceType declaringType) throws RandoopTypeException {
    MethodCall op = new MethodCall(m);
    List<GeneralType> paramTypes = new ArrayList<>();
    paramTypes.add(declaringType);
    for (Class<?> pc : m.getParameterTypes()) {
      paramTypes.add(GeneralType.forClass(pc));
    }
    GeneralType outputType = GeneralType.forClass(m.getReturnType());
    return new TypedClassOperation(op, declaringType, new TypeTuple(paramTypes), outputType);
  }
  
}
