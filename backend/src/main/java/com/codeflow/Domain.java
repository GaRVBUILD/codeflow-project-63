package com.codeflow;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.*;

@Entity @Table(name="users") class AppUser {
 public AppUser(){}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false) String name;
 @Column(nullable=false,unique=true) String email;
 @Column(nullable=false) String password;
 @Column(nullable=false) String role="USER";
 @Column(nullable=false,columnDefinition="integer not null default 0") int tokenVersion=0;
 Instant createdAt=Instant.now();
}

@Entity @Table(name="categories") @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) class Category {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false,unique=true) String name;
 String description;
 public Category(){} public Category(String name){this.name=name;}
}

@Entity @Table(name="tasks", indexes={@Index(columnList="ownerId"),@Index(columnList="deadline")}) @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) class Task {
 public Task(){}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false) Long ownerId;
 @Column(nullable=false) String title;
 @Column(length=4000) String description;
 LocalDateTime deadline;
 @Column(nullable=false) int importance=3;
 String status="PENDING";
 String category="General";
 double priorityScore;
 String priorityLevel="MEDIUM";
 Instant createdAt=Instant.now(),updatedAt=Instant.now();
}

@Entity @Table(name="time_logs") @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) class TimeLog {
 public TimeLog(){}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 Long taskId,userId; Instant startTime,endTime; long durationSeconds;
}

@Entity @Table(name="prioritization_rules") @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) class PriorityRule {
 public PriorityRule(){}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id=1L;
 double deadlineWeight=0.6,importanceWeight=0.4;
}

@Entity @Table(name="password_reset_tokens", indexes={@Index(columnList="email,createdAt"),@Index(columnList="tokenHash",unique=true)}) class PasswordResetToken {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false) String email;
 String tokenHash;
 @Column(nullable=false) Instant createdAt=Instant.now();
 Instant expiresAt;
 Instant usedAt;
 public PasswordResetToken(){}
}
