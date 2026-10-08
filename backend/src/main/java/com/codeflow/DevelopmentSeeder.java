package com.codeflow;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Development-only sample accounts and activity; enable with app.seed.enabled=true. */
@Component
@ConditionalOnProperty(name="app.seed.enabled", havingValue="true")
class DevelopmentSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final TaskRepository tasks;
    private final CategoryRepository categories;
    private final TimeLogRepository timeLogs;
    private final RuleRepository rules;
    private final PasswordEncoder passwords;

    DevelopmentSeeder(UserRepository users, TaskRepository tasks, CategoryRepository categories,
                      TimeLogRepository timeLogs, RuleRepository rules, PasswordEncoder passwords) {
        this.users=users; this.tasks=tasks; this.categories=categories; this.timeLogs=timeLogs;
        this.rules=rules; this.passwords=passwords;
    }

    @Override public void run(String... args) {
        AppUser demo=account("demo@codeflow.local", "Demo User", "Demo1234!", "USER");
        account("admin@codeflow.local", "CodeFlow Admin", "AdminDemo123!", "ADMIN");
        for(String name:List.of("Study","Work","Personal"))
            if(categories.findAll().stream().noneMatch(c->c.name.equalsIgnoreCase(name))) categories.save(new Category(name));
        if(rules.findById(1L).isEmpty()) { PriorityRule rule=new PriorityRule(); rule.id=1L; rule.deadlineWeight=.6; rule.importanceWeight=.4; rules.save(rule); }
        LocalDateTime now=LocalDateTime.now();
        Task report=sampleTask(demo.id,"Prepare project progress report","Summarize this week's milestones and open questions.",now.plusDays(1),5,"Work",.82,"CRITICAL");
        Task revision=sampleTask(demo.id,"Review database normalization","Revisit 2NF and 3NF examples before the quiz.",now.plusDays(3),4,"Study",.55,"HIGH");
        sampleTask(demo.id,"Plan a weekend walk","Pick a route and invite a friend.",now.plusDays(8),2,"Personal",.25,"MEDIUM");
        sampleTask(demo.id,"Organize lecture notes","File notes from the last two classes.",null,2,"Study",.16,"LOW");
        Task completed=sampleTask(demo.id,"Set up CodeFlow workspace","Create the first workspace and outline initial tasks.",now.minusDays(1),3,"Work",0,"LOW");
        completed.status="COMPLETED"; tasks.save(completed);

        if(timeLogs.findByTaskIdAndUserIdOrderByStartTimeDesc(revision.id,demo.id).isEmpty()){
            TimeLog finished=new TimeLog(); finished.taskId=revision.id; finished.userId=demo.id;
            finished.startTime=Instant.now().minus(2,ChronoUnit.DAYS).minus(35,ChronoUnit.MINUTES);
            finished.endTime=Instant.now().minus(2,ChronoUnit.DAYS); finished.durationSeconds=35*60; timeLogs.save(finished);
        }
        if(timeLogs.findByTaskIdAndUserIdOrderByStartTimeDesc(completed.id,demo.id).isEmpty()){
            TimeLog earlier=new TimeLog(); earlier.taskId=completed.id; earlier.userId=demo.id;
            earlier.startTime=Instant.now().minus(1,ChronoUnit.DAYS).minus(52,ChronoUnit.MINUTES);
            earlier.endTime=Instant.now().minus(1,ChronoUnit.DAYS); earlier.durationSeconds=52*60; timeLogs.save(earlier);
        }
    }

    private AppUser account(String email,String name,String password,String role) {
        return users.findByEmail(email).orElseGet(()->{AppUser u=new AppUser();u.name=name;u.email=email;u.password=passwords.encode(password);u.role=role;return users.save(u);});
    }
    private Task task(Long owner,String title,String description,LocalDateTime deadline,int importance,String category,double score,String level) {
        Task t=new Task();t.ownerId=owner;t.title=title;t.description=description;t.deadline=deadline;t.importance=importance;
        t.category=category;t.status="PENDING";t.priorityScore=score;t.priorityLevel=level;return tasks.save(t);
    }
    private Task sampleTask(Long owner,String title,String description,LocalDateTime deadline,int importance,String category,double score,String level){
        return tasks.findByOwnerIdOrderByPriorityScoreDesc(owner).stream().filter(t->t.title.equals(title)).findFirst()
                .orElseGet(()->task(owner,title,description,deadline,importance,category,score,level));
    }
}
