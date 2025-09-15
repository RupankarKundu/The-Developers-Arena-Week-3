import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** --------- Custom Exceptions --------- **/
class BankException extends RuntimeException {
    public BankException(String msg) { super(msg); }
}

class AccountNotFoundException extends BankException {
    public AccountNotFoundException(String id) { super("Account not found: " + id); }
}

class IllegalAmountException extends BankException {
    public IllegalAmountException(double amt) { super("Illegal amount: " + amt); }
}

class InsufficientFundsException extends BankException {
    public InsufficientFundsException(String msg) { super(msg); }
}

/** --------- Domain Model --------- **/
final class Customer {
    private final String id;
    private final String name;

    public Customer(String id, String name) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
    }

    public String id() { return id; }
    public String name() { return name; }
}

interface InterestBearing {
    double getAnnualRate();
    default double monthlyInterest(double balance) {
        return balance * (getAnnualRate() / 12.0);
    }
}

abstract class Account {
    private final String id;
    private final Customer owner;
    protected double balance;

    protected Account(String id, Customer owner, double initial) {
        if (initial < 0) throw new IllegalAmountException(initial);
        this.id = Objects.requireNonNull(id);
        this.owner = Objects.requireNonNull(owner);
        this.balance = initial;
    }

    public String id() { return id; }
    public Customer owner() { return owner; }
    public double balance() { return balance; }

    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalAmountException(amount);
        balance += amount;
    }

    public abstract void withdraw(double amount);
    public abstract String type();

    @Override
    public String toString() {
        return "%s{id='%s', owner='%s', balance=%.2f}"
                .formatted(type(), id, owner.name(), balance);
    }
}

class SavingsAccount extends Account implements InterestBearing {
    private final double annualRate;

    public SavingsAccount(String id, Customer owner, double initial, double annualRate) {
        super(id, owner, initial);
        if (annualRate < 0) throw new IllegalArgumentException("Rate must be >= 0");
        this.annualRate = annualRate;
    }

    @Override public double getAnnualRate() { return annualRate; }

    @Override
    public void withdraw(double amount) {
        if (amount <= 0) throw new IllegalAmountException(amount);
        if (amount > balance) throw new InsufficientFundsException("Savings cannot go negative");
        balance -= amount;
    }

    @Override public String type() { return "SavingsAccount"; }

    public void applyMonthlyInterest() {
        double interest = monthlyInterest(balance);
        balance += interest;
    }
}

class CurrentAccount extends Account {
    private final double overdraftLimit;

    public CurrentAccount(String id, Customer owner, double initial, double overdraftLimit) {
        super(id, owner, initial);
        if (overdraftLimit < 0) throw new IllegalArgumentException("Overdraft must be >= 0");
        this.overdraftLimit = overdraftLimit;
    }

    @Override
    public void withdraw(double amount) {
        if (amount <= 0) throw new IllegalAmountException(amount);
        double next = balance - amount;
        if (next < -overdraftLimit) {
            throw new InsufficientFundsException(
                    "Overdraft exceeded. Limit=" + overdraftLimit + ", attempted=" + amount);
        }
        balance = next;
    }

    @Override public String type() { return "CurrentAccount"; }
}

/** --------- Bank Aggregate --------- **/
class Bank {
    private final String name;
    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, Customer> customers = new HashMap<>();
    private final AtomicInteger seq = new AtomicInteger(1000);

    public Bank(String name) { this.name = Objects.requireNonNull(name); }

    private String nextId(String prefix) { return prefix + "-" + seq.incrementAndGet(); }

    private Customer getOrCreateCustomer(String name) {
        return customers.computeIfAbsent(name.toLowerCase(), k ->
                new Customer("CUST-" + customers.size(), name));
    }

    public String openSavingsAccount(String customerName, double initial, double annualRate) {
        Customer c = getOrCreateCustomer(customerName);
        String id = nextId("SAV");
        Account acc = new SavingsAccount(id, c, initial, annualRate);
        accounts.put(id, acc);
        return id;
    }

    public String openCurrentAccount(String customerName, double initial, double overdraftLimit) {
        Customer c = getOrCreateCustomer(customerName);
        String id = nextId("CUR");
        Account acc = new CurrentAccount(id, c, initial, overdraftLimit);
        accounts.put(id, acc);
        return id;
    }

    private Account require(String id) {
        Account a = accounts.get(id);
        if (a == null) throw new AccountNotFoundException(id);
        return a;
    }

    public void deposit(String accountId, double amount) {
        require(accountId).deposit(amount);
    }

    public void withdraw(String accountId, double amount) {
        require(accountId).withdraw(amount);
    }

    public void transfer(String fromId, String toId, double amount) {
        if (amount <= 0) throw new IllegalAmountException(amount);
        Account from = require(fromId);
        Account to = require(toId);
        from.withdraw(amount);
        to.deposit(amount);
    }

    public double getBalance(String accountId) {
        return require(accountId).balance();
    }

    public void applyMonthlyUpdates() {
        for (Account a : accounts.values()) {
            if (a instanceof SavingsAccount s) s.applyMonthlyInterest();
        }
    }

    public void printAllAccounts() {
        System.out.println("=== " + name + " Accounts ===");
        accounts.values().stream()
                .sorted(Comparator.comparing(Account::id))
                .forEach(System.out::println);
        System.out.println();
    }
}

/** --------- User-Friendly Menu --------- **/
public class Bank_App {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Bank bank = new Bank("Demo Bank");

        while (true) {
            System.out.println("\n===== BANK MENU =====");
            System.out.println("1. Open Savings Account");
            System.out.println("2. Open Current Account");
            System.out.println("3. Deposit");
            System.out.println("4. Withdraw");
            System.out.println("5. Transfer");
            System.out.println("6. Check Balance");
            System.out.println("7. Apply Monthly Updates");
            System.out.println("8. Show All Accounts");
            System.out.println("9. Exit");
            System.out.print("Choose an option: ");

            int choice;
            try { choice = Integer.parseInt(sc.nextLine()); }
            catch (Exception e) { System.out.println("Invalid input."); continue; }

            try {
                switch (choice) {
                    case 1 -> {
                        System.out.print("Customer name: ");
                        String name = sc.nextLine();
                        System.out.print("Initial deposit: ");
                        double initial = Double.parseDouble(sc.nextLine());
                        System.out.print("Annual interest rate (e.g., 0.05 for 5%): ");
                        double rate = Double.parseDouble(sc.nextLine());
                        String id = bank.openSavingsAccount(name, initial, rate);
                        System.out.println("Savings account created with ID: " + id);
                    }
                    case 2 -> {
                        System.out.print("Customer name: ");
                        String name = sc.nextLine();
                        System.out.print("Initial deposit: ");
                        double initial = Double.parseDouble(sc.nextLine());
                        System.out.print("Overdraft limit: ");
                        double limit = Double.parseDouble(sc.nextLine());
                        String id = bank.openCurrentAccount(name, initial, limit);
                        System.out.println("Current account created with ID: " + id);
                    }
                    case 3 -> {
                        System.out.print("Account ID: ");
                        String id = sc.nextLine();
                        System.out.print("Amount: ");
                        double amt = Double.parseDouble(sc.nextLine());
                        bank.deposit(id, amt);
                        System.out.println("Deposit successful.");
                    }
                    case 4 -> {
                        System.out.print("Account ID: ");
                        String id = sc.nextLine();
                        System.out.print("Amount: ");
                        double amt = Double.parseDouble(sc.nextLine());
                        bank.withdraw(id, amt);
                        System.out.println("Withdrawal successful.");
                    }
                    case 5 -> {
                        System.out.print("From Account ID: ");
                        String from = sc.nextLine();
                        System.out.print("To Account ID: ");
                        String to = sc.nextLine();
                        System.out.print("Amount: ");
                        double amt = Double.parseDouble(sc.nextLine());
                        bank.transfer(from, to, amt);
                        System.out.println("Transfer successful.");
                    }
                    case 6 -> {
                        System.out.print("Account ID: ");
                        String id = sc.nextLine();
                        System.out.printf("Balance = %.2f%n", bank.getBalance(id));
                    }
                    case 7 -> {
                        bank.applyMonthlyUpdates();
                        System.out.println("Monthly updates applied.");
                    }
                    case 8 -> bank.printAllAccounts();
                    case 9 -> {
                        System.out.println("Exiting. Goodbye!");
                        sc.close();
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (BankException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Unexpected error: " + e);
            }
        }
    }
}
