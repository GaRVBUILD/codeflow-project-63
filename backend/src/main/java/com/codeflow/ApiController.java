package com.codeflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.*;
import java.util.stream.*;

@RestController @RequestMapping("/api")
class ApiController {
 private final UserRepository users; private final TaskRepository tasks; private final TimeLogRepository logs; private final CategoryRepository categories; private final RuleRepository rules; private final PasswordResetRepository resets; private final PasswordEncoder passwords; private final JwtService jwt; private final JavaMailSender mail; private final boolean mailEnabled; private final String mailFrom,frontendUrl;
 ApiController(UserRepository u,TaskRepository t,TimeLogRepository l,CategoryRepository c,RuleRepository r,PasswordResetRepository resets,PasswordEncoder p,JwtService j,JavaMailSender mail,@Value("${app.mail.enabled:false}") boolean mailEnabled,@Value("${app.mail.from}") String mailFrom,@Value("${app.frontend.url}") String frontendUrl){users=u;tasks=t;logs=l;categories=c;rules=r;this.resets=resets;passwords=p;jwt=j;this.mail=mail;this.mailEnabled=mailEnabled;this.mailFrom=mailFrom;this.frontendUrl=frontendUrl;}
 record Register(@NotBlank String name,@Email @NotBlank String email,@Size(min=8) String password){}
 record Login(@Email String email,@NotBlank String password){}
 record PasswordResetRequest(@Email @NotBlank String email){}
 record PasswordResetConfirm(@NotBlank String token,@NotBlank @Size(min=8) String newPassword){}
 record TaskInput(@NotBlank @Size(max=180) String title,String description,LocalDateTime deadline,@Min(1) @Max(5) Integer importance,String status,String category){}
 record RuleInput(@DecimalMin("0.0") @DecimalMax("1.0") double deadlineWeight,@DecimalMin("0.0") @DecimalMax("1.0") double importanceWeight){}
 private AppUser user(String email){return users.findByEmail(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));}
 private Task owned(String email,long id){Task t=tasks.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!t.ownerId.equals(user(email).id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);return t;}
 @PostMapping("/auth/register") Map<String,Object> register(@Valid @RequestBody Register in){if(users.findByEmail(in.email()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");AppUser u=new AppUser();u.name=in.name();u.email=in.email().toLowerCase();u.password=passwords.encode(in.password());u.role="USER";users.save(u);return auth(u);}
 @PostMapping("/auth/login") Map<String,Object> login(@Valid @RequestBody Login in){AppUser u=users.findByEmail(in.email().toLowerCase()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials"));if(!passwords.matches(in.password(),u.password))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");return auth(u);}
 @PostMapping("/auth/password-reset/request") ResponseEntity<Map<String,String>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest in){
  if(!mailEnabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Password reset email is not configured. Contact the administrator.");
  String email=in.email().toLowerCase(Locale.ROOT);Instant now=Instant.now();String message="If an account with that email exists, a password reset link will be sent.";
  if(resets.countByEmailAndCreatedAtAfter(email,now.minus(1,ChronoUnit.HOURS))>=3)return ResponseEntity.accepted().body(Map.of("message",message));
  AppUser account=users.findByEmail(email).orElse(null);
  if(account==null){PasswordResetToken marker=new PasswordResetToken();marker.email=email;marker.usedAt=now;resets.save(marker);return ResponseEntity.accepted().body(Map.of("message",message));}
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  PasswordResetToken record=new PasswordResetToken();record.email=email;record.tokenHash=hashToken(raw);record.createdAt=now;record.expiresAt=now.plus(30,ChronoUnit.MINUTES);resets.save(record);
  SimpleMailMessage emailMessage=new SimpleMailMessage();emailMessage.setFrom(mailFrom);emailMessage.setTo(email);emailMessage.setSubject("Reset your CodeFlow password");emailMessage.setText("Use this one-time link within 30 minutes to choose a new password:\n\n"+frontendUrl+"/reset-password?token="+raw+"\n\nIf you did not request this, you can ignore this email.");
  try{mail.send(emailMessage);}catch(MailException ex){record.usedAt=Instant.now();resets.save(record);System.getLogger(ApiController.class.getName()).log(System.Logger.Level.WARNING,"Password reset email delivery failed.");}
  return ResponseEntity.accepted().body(Map.of("message",message));
 }
 @PostMapping("/auth/password-reset/confirm") Map<String,String> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirm in){
  Instant now=Instant.now();PasswordResetToken reset=resets.findByTokenHashAndUsedAtIsNull(hashToken(in.token())).filter(r->r.expiresAt!=null&&r.expiresAt.isAfter(now)).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reset link is invalid or expired."));
  AppUser account=users.findByEmail(reset.email).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reset link is invalid or expired."));account.password=passwords.encode(in.newPassword());account.tokenVersion++;users.save(account);
  reset.usedAt=now;resets.save(reset);resets.findByEmailAndUsedAtIsNull(account.email).forEach(other->{other.usedAt=now;resets.save(other);});return Map.of("message","Password updated. You can now sign in.");
 }
 private String hashToken(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Could not hash reset token",e);}}
 private Map<String,Object> auth(AppUser u){return Map.of("token",jwt.create(u),"user",Map.of("id",u.id,"name",u.name,"email",u.email,"role",u.role));}
 @GetMapping("/auth/me") Map<String,Object> me(java.security.Principal p){AppUser u=user(p.getName());return Map.of("id",u.id,"name",u.name,"email",u.email,"role",u.role);}
 @GetMapping("/tasks") List<Task> list(java.security.Principal p,@RequestParam(required=false) String q,@RequestParam(required=false) String status,@RequestParam(required=false) String priority){String query=q==null?"":q.toLowerCase();return tasks.findByOwnerIdOrderByPriorityScoreDesc(user(p.getName()).id).stream().filter(t->(t.title+" "+Objects.toString(t.description,"")).toLowerCase().contains(query)).filter(t->status==null||t.status.equalsIgnoreCase(status)).filter(t->priority==null||t.priorityLevel.equalsIgnoreCase(priority)).toList();}
 @PostMapping("/tasks") Task create(java.security.Principal p,@Valid @RequestBody TaskInput in){Task t=new Task();t.ownerId=user(p.getName()).id;apply(t,in);return tasks.save(score(t));}
 @GetMapping("/tasks/{id}") Task get(java.security.Principal p,@PathVariable long id){return owned(p.getName(),id);}
 @PutMapping("/tasks/{id}") Task update(java.security.Principal p,@PathVariable long id,@Valid @RequestBody TaskInput in){Task t=owned(p.getName(),id);apply(t,in);t.updatedAt=Instant.now();return tasks.save(score(t));}
 @DeleteMapping("/tasks/{id}") ResponseEntity<Void> delete(java.security.Principal p,@PathVariable long id){tasks.delete(owned(p.getName(),id));return ResponseEntity.noContent().build();}
 private void apply(Task t,TaskInput i){t.title=i.title();t.description=i.description();t.deadline=i.deadline();t.importance=i.importance()==null?3:i.importance();t.status=i.status()==null?"PENDING":i.status().toUpperCase();t.category=i.category()==null?"General":i.category();}
 private Task score(Task t){PriorityRule r=rules.findById(1L).orElseGet(()->rules.save(new PriorityRule()));double urgency=0;if(t.deadline!=null){long hrs=Duration.between(LocalDateTime.now(),t.deadline).toHours();urgency=hrs<0?1.0:Math.max(0,Math.min(1,1.0/(1.0+hrs/24.0)));}t.priorityScore=t.status.equals("COMPLETED")?0:Math.round((t.importance/5.0*r.importanceWeight+urgency*r.deadlineWeight)*1000)/1000.0;t.priorityLevel=t.priorityScore>=.72?"CRITICAL":t.priorityScore>=.48?"HIGH":t.priorityScore>=.25?"MEDIUM":"LOW";return t;}
 @PostMapping("/tasks/{id}/timer/start") TimeLog start(java.security.Principal p,@PathVariable long id){AppUser u=user(p.getName());Task t=owned(p.getName(),id);if(logs.findFirstByUserIdAndEndTimeIsNull(u.id).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Stop your active timer first");TimeLog l=new TimeLog();l.taskId=t.id;l.userId=u.id;l.startTime=Instant.now();return logs.save(l);}
 @PostMapping("/tasks/{id}/timer/stop") TimeLog stop(java.security.Principal p,@PathVariable long id){AppUser u=user(p.getName());owned(p.getName(),id);TimeLog l=logs.findFirstByUserIdAndEndTimeIsNull(u.id).filter(x->x.taskId.equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No active timer"));l.endTime=Instant.now();l.durationSeconds=Duration.between(l.startTime,l.endTime).getSeconds();return logs.save(l);}
 @GetMapping("/tasks/{id}/time-logs") List<TimeLog> timeLogs(java.security.Principal p,@PathVariable long id){owned(p.getName(),id);return logs.findByTaskIdAndUserIdOrderByStartTimeDesc(id,user(p.getName()).id);}
 @GetMapping("/timer/active") Object active(java.security.Principal p){return logs.findFirstByUserIdAndEndTimeIsNull(user(p.getName()).id).orElse(null);}
 @GetMapping("/categories") List<Category> categoryList(){return categories.findAll();}
 @GetMapping("/progress") Map<String,Object> progress(java.security.Principal p){List<Task> all=list(p,null,null,null);Set<Long> taskIds=all.stream().map(t->t.id).collect(Collectors.toSet());long done=all.stream().filter(t->t.status.equals("COMPLETED")).count(),overdue=all.stream().filter(t->t.deadline!=null&&t.deadline.isBefore(LocalDateTime.now())&&!t.status.equals("COMPLETED")).count();long secs=logs.findAll().stream().filter(l->l.userId.equals(user(p.getName()).id)&&taskIds.contains(l.taskId)).mapToLong(l->l.durationSeconds+(l.endTime==null?Duration.between(l.startTime,Instant.now()).getSeconds():0)).sum();return Map.of("totalTasks",all.size(),"completed",done,"pending",all.size()-done,"overdue",overdue,"completionRate",all.isEmpty()?0:Math.round(done*1000.0/all.size())/10.0,"timeTrackedSeconds",secs,"priorities",all.stream().collect(Collectors.groupingBy(t->t.priorityLevel,Collectors.counting())));}
 @GetMapping("/progress/daily") List<Map<String,Object>> daily(java.security.Principal p){Long userId=user(p.getName()).id;Set<Long> taskIds=tasks.findByOwnerIdOrderByPriorityScoreDesc(userId).stream().map(t->t.id).collect(Collectors.toSet());return logs.findAll().stream().filter(l->l.userId.equals(userId)&&taskIds.contains(l.taskId)&&l.endTime!=null).collect(Collectors.groupingBy(l->l.startTime.atZone(ZoneOffset.UTC).toLocalDate().toString(),TreeMap::new,Collectors.summingLong(l->l.durationSeconds))).entrySet().stream().map(e->Map.<String,Object>of("date",e.getKey(),"seconds",e.getValue())).toList();}
 @GetMapping("/progress/weekly") List<Map<String,Object>> weekly(java.security.Principal p){return daily(p);}
 @GetMapping("/admin/metrics") Map<String,Object> metrics(){
  List<Task> all=tasks.findAll();Set<Long> validTaskIds=all.stream().map(t->t.id).collect(Collectors.toSet());List<TimeLog> allLogs=logs.findAll().stream().filter(l->validTaskIds.contains(l.taskId)).toList();Instant now=Instant.now();
  long completed=all.stream().filter(t->t.status.equals("COMPLETED")).count();
  long active=allLogs.stream().filter(l->l.endTime==null).count();
  long trackedSeconds=allLogs.stream().mapToLong(l->l.durationSeconds+(l.endTime==null&&l.startTime!=null?Duration.between(l.startTime,now).getSeconds():0)).sum();
  Map<LocalDate,Long> byDay=all.stream().filter(t->t.createdAt!=null).collect(Collectors.groupingBy(t->t.createdAt.atZone(ZoneOffset.UTC).toLocalDate(),TreeMap::new,Collectors.counting()));
  List<Map<String,Object>> trend=new ArrayList<>();LocalDate today=LocalDate.now(ZoneOffset.UTC);
  for(int i=13;i>=0;i--){LocalDate day=today.minusDays(i);trend.add(Map.of("date",day.toString(),"tasks",byDay.getOrDefault(day,0L)));}
  Map<String,Long> statuses=all.stream().collect(Collectors.groupingBy(t->t.status,TreeMap::new,Collectors.counting()));
  Map<String,Long> priorities=all.stream().collect(Collectors.groupingBy(t->t.priorityLevel,TreeMap::new,Collectors.counting()));
  var runtime=java.lang.management.ManagementFactory.getRuntimeMXBean();var memory=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
  Map<String,Object> result=new LinkedHashMap<>();result.put("users",users.count());result.put("tasks",all.size());result.put("completed",completed);result.put("activeTimers",active);result.put("categories",categories.count());
  result.put("totalTrackedSeconds",trackedSeconds);result.put("totalTrackedHours",Math.round(trackedSeconds/360.0)/10.0);result.put("taskTrend",trend);result.put("statusDistribution",statuses);result.put("priorityDistribution",priorities);
  result.put("uptimeSeconds",runtime.getUptime()/1000);result.put("heapUsedMb",memory.getUsed()/1024/1024);result.put("heapMaxMb",memory.getMax()/1024/1024);result.put("snapshotAt",now.toString());return result;
 }
 @GetMapping("/admin/users") List<Map<String,Object>> adminUsers(){List<Task> allTasks=tasks.findAll();Set<Long> validTaskIds=allTasks.stream().map(t->t.id).collect(Collectors.toSet());List<TimeLog> allLogs=logs.findAll().stream().filter(l->validTaskIds.contains(l.taskId)).toList();return users.findAll().stream().map(u->{Map<String,Object> row=new LinkedHashMap<>();long userTasks=allTasks.stream().filter(t->t.ownerId.equals(u.id)).count();long seconds=allLogs.stream().filter(l->l.userId.equals(u.id)).mapToLong(l->l.durationSeconds+(l.endTime==null&&l.startTime!=null?Duration.between(l.startTime,Instant.now()).getSeconds():0)).sum();row.put("id",u.id);row.put("name",u.name);row.put("email",u.email);row.put("role",u.role);row.put("createdAt",u.createdAt);row.put("taskCount",userTasks);row.put("trackedSeconds",seconds);return row;}).toList();}
 @GetMapping("/admin/categories") List<Category> adminCategories(){return categories.findAll();}
 @GetMapping("/admin/rules") PriorityRule getRules(){return rules.findById(1L).orElseGet(()->rules.save(new PriorityRule()));}
 @PutMapping("/admin/rules") PriorityRule setRules(@Valid @RequestBody RuleInput i){if(Math.abs(i.deadlineWeight()+i.importanceWeight()-1)>0.001)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Weights must add up to 1");PriorityRule r=getRules();r.deadlineWeight=i.deadlineWeight();r.importanceWeight=i.importanceWeight();rules.save(r);tasks.findAll().forEach(t->tasks.save(score(t)));return r;}
 @PostMapping("/admin/categories") Category addCategory(@RequestBody Map<String,String> body){return categories.save(new Category(body.get("name")));}
 @PutMapping("/admin/categories/{id}") Category editCategory(@PathVariable long id,@RequestBody Map<String,String> body){Category c=categories.findById(id).orElseThrow();c.name=body.get("name");c.description=body.get("description");return categories.save(c);}
 @DeleteMapping("/admin/categories/{id}") void removeCategory(@PathVariable long id){categories.deleteById(id);}
}
