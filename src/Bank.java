import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.Semaphore;


public class Bank {

    public static final int CUST_NUM = 5; // this will be 5 for the final submission
    public static final int TELLER_NUM = 2;
    public static final int LOAN_OFFICER_NUM = 1;

    // Teller Semaphores
    protected static Semaphore teller_line_resource = new Semaphore(1, true);
    protected static Semaphore num_in_teller_line = new Semaphore(0, true);
    protected static Semaphore teller_transaction = new Semaphore(0, true);
    protected static Semaphore teller_receipt = new Semaphore(0, true);
    protected static Semaphore teller_resource = new Semaphore(TELLER_NUM, true);
    protected static Semaphore sit_down_w_teller = new Semaphore(0, true);


    // Loan Officer semaphores
    protected static Semaphore loan_line_resource = new Semaphore(1, true);
    protected static Semaphore loan_officer_resource = new Semaphore(LOAN_OFFICER_NUM, true);
    protected static Semaphore num_in_loan_line = new Semaphore(0, true);
    protected static Semaphore loan_transaction = new Semaphore(0, true);
    protected static Semaphore received_loan = new Semaphore(0, true);
    protected static Semaphore sit_down_w_LO = new Semaphore(0, true);


    public static LinkedList<Customer> teller_line = new LinkedList<>();
    public static LinkedList<Customer> loan_line = new LinkedList<>();

    Bank(){}

    public static void runBank() {
        Thread[] cust_threads = new Thread[CUST_NUM];
        Thread[] teller_threads = new Thread[2];
        Thread loan_thread;

        System.out.println("BANK IS NOW OPEN\n");

        for (int i = 0; i < 2; i++) {
            teller_threads[i] = new Thread(new Teller(i + 1));
            teller_threads[i].start();
        }
        loan_thread = new Thread(new LoanOfficer(1));
        loan_thread.start();

        Customer[] customers = new Customer[CUST_NUM];
        for (int i = 0; i < CUST_NUM; i++) {
            customers[i] = new Customer(i + 1);
        }
        for (int i = 0; i < CUST_NUM; i++) {
            cust_threads[i] = new Thread(customers[i]);
            cust_threads[i].start();
        }


        for (int i = 0; i < CUST_NUM; i++) {
            try {
                cust_threads[i].join();
                System.out.println("Customer " + customers[i].getId() + " is joined by main");
            } catch (InterruptedException e) {
                System.err.println("BIG OOF ERROR OCCURRED WHEN TRYING TO JOIN CUSTOMER THREADS");
            }
        }

        System.out.println("BANK IS NOW CLOSED");
        // last step
        System.out.println("\n\nBANK SUMMARY");
        System.out.printf("          %25s %15s %n", "Ending Balance", "Loan Amount");
        int balTotal = 0, loanTotal = 0;
        for (int i = 0; i < CUST_NUM; i++) {
            // final report for each customer, their ending balance, and their loan amount
            System.out.printf("Customer %d%25d %15d %n", customers[i].getId(), customers[i].getBalance(), customers[i].getLoan());
            balTotal += customers[i].getBalance();
            loanTotal += customers[i].getLoan();
        }
        System.out.println();
        System.out.printf("Totals    %25d %15d %n", balTotal, loanTotal);
        System.exit(0);
    }

}   // END OF PROJECT 2 MAIN DRIVER CLASS


class Customer implements Runnable {

    private int id;
    private int balance;
    private int loan;

    Customer(int id) {
        this.id = id;
        balance = 1000;
        loan = 0;
    }

    public int getId() {
        return id;
    }

    public int getBalance() {
        return balance;
    }

    public int getLoan() {
        return loan;
    }

    public int deposit() { // returns the random deposit amount
        Random r = new Random();
        int d = (r.nextInt(5) + 1) * 100;
        balance += d;
        return d;
    }

    public int withdraw() { // returns the random withdraw amount
        Random r = new Random();
        int w = (r.nextInt(5) + 1) * 100;
        balance -= w;
        return w;
    }

    public int generateLoan() { // returns the random loan generated
        Random r = new Random();
        int l = (r.nextInt(5) + 1) * 100; // generates a loan rannging from 100 to 500
        balance += l;
        loan += l;
        return l;
    }


    @Override
    public void run() {
        System.out.println("Customer " + id + " created");
        try {
            Thread.sleep(1);

            for (int visits = 0; visits < 3; visits++) {
                Random r = new Random();
                int action = r.nextInt(2);

                switch (action) {
                    case 0: { // go to the teller line
                        System.out.println("Customer " + this.id + " Got in teller line");
                        Bank.teller_line_resource.acquire();
                        Bank.teller_line.addLast(this);
                        Bank.num_in_teller_line.release();
                        Bank.teller_line_resource.release();


                        Bank.teller_resource.acquire();
                        Bank.sit_down_w_teller.release();
//                        Bank.teller_ready.acquire();
                        Bank.teller_transaction.acquire();
                        Bank.teller_receipt.release();

                        break;
                    }

                    case 1: { // go to the loan officer line

                        // need to make sure no one else is using this resource, MUTEX for loan_line
                        Bank.loan_line_resource.acquire();
                        Bank.loan_line.addLast(this);
                        Bank.num_in_loan_line.release();
                        Bank.loan_line_resource.release();

                        Bank.loan_officer_resource.acquire();
                        Bank.sit_down_w_LO.release(); // sit down with the loan officer

                        Bank.loan_transaction.acquire(); // loan officer processes transaction
                        Bank.received_loan.release();   // customer acknowledges transaction

                        Bank.loan_officer_resource.release(); // customer leaves the room
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + action);
                }


            }


            System.out.println("Customer " + id + " has left the bank");
        } catch (InterruptedException e) {
            System.err.println("InterruptedException caught in the Customer " + this.id + " run()");
        }
    }

}       // END OF CUSTOMER CLASS


class Teller implements Runnable {

    private int id;

    Teller(int id) {
        this.id = id;
    }

    @Override
    public void run() {
        System.out.println("Teller " + this.id + " created");
        try {
            while (true) {


                Thread.sleep(1);
                Bank.num_in_teller_line.acquire();

                // mutex stuff for the LinkedList resource
                Bank.teller_line_resource.acquire();
                Customer c = Bank.teller_line.removeFirst();
                Bank.teller_line_resource.release();
                // end mutex

                Bank.sit_down_w_teller.acquire();
//                Bank.teller_ready.release();
                System.out.println("Teller " + this.id + " begins serving Customer " + c.getId());

                int trans_type = (new Random()).nextInt(2);
                switch (trans_type) {
                    case 0: { // deposit is being made
                        int depo = c.deposit();
                        System.out.println("Customer " + c.getId() + " requests of teller " + this.id + " to make a deposit of $" + depo);
                        Bank.teller_transaction.release();
                        System.out.println("Teller " + this.id + " processes deposit of $" + depo + " for Customer " + c.getId());
                        Bank.teller_receipt.acquire();
                        System.out.println("Customer " + c.getId() + " gets cash and receipt from Teller " + this.id);
                        break;
                    }
                    case 1: { // withdraw is being made
                        System.out.println("in withdraw");
                        int with = c.withdraw();
                        System.out.println("Customer " + c.getId() + " requests of teller " + this.id + " to make a withdrawal of $" + with);
                        Bank.teller_transaction.release();
                        System.out.println("Teller " + this.id + " processes withdrawal of $" + with + " for Customer " + c.getId());
                        Bank.teller_receipt.acquire();
                        System.out.println("Customer " + c.getId() + " gets receipt from Teller " + this.id);
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + trans_type);

                }
                Bank.teller_resource.release();


            }

        } catch (InterruptedException e) {
            System.err.println("InterruptedException caught in the Teller run()");
        }
    }
}       // END OF TELLER CLASS


class LoanOfficer implements Runnable {

    private int id;

    LoanOfficer(int id) {
        this.id = id;
    }

    @Override
    public void run() {
        System.out.println("Loan Officer created");

        try {
            while (true) {

                Thread.sleep(1);
                Bank.num_in_loan_line.acquire();

                // mutex stuff
                Bank.loan_line_resource.acquire();
                Customer c = Bank.loan_line.removeFirst();
                Bank.loan_line_resource.release();
                // end mutex

                Bank.sit_down_w_LO.acquire(); // loan officer now knows that customer is ready
                System.out.println("Loan Officer begins serving Customer " + c.getId());
                int loan = c.generateLoan();
                System.out.println("Customer " + c.getId() + " requests of Loan Officer to apply for a loan of $" + loan);
                Bank.loan_transaction.release();
                // now we wait for customer to give the loan officer a transaction confirmation
                System.out.println("Loan Officer approves of loan for Customer " + c.getId());
                Bank.received_loan.acquire();
                System.out.println("Customer " + c.getId() + " gets loan from Loan Officer");
            }
        } catch (InterruptedException e) {
            System.err.println("InterruptedException caught in the LoanOfficer run()");
        }

    }
}       // END OF LOANOFFICER CLASS



