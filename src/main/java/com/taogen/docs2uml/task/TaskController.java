package com.taogen.docs2uml.task;

import com.taogen.docs2uml.commons.constant.CrawlerType;
import com.taogen.docs2uml.commons.constant.EntityType;
import com.taogen.docs2uml.commons.constant.ParserType;
import com.taogen.docs2uml.commons.entity.CommandOption;
import com.taogen.docs2uml.commons.entity.MyEntity;
import com.taogen.docs2uml.commons.exception.TaskExecuteException;
import com.taogen.docs2uml.commons.util.GenericUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author Taogen
 */
public class TaskController {
    private static final Logger logger = LogManager.getLogger();
    /**
     * Thread pool size.
     * Warning: Too big too fast can lead to request of destination server be refused. It will throw java.net.SocketTimeoutException: Read timed out
     */
    private static final Integer THREAD_POOL_COUNT = 5;
    private final List<MyEntity> myEntities = new ArrayList<>();
    private final List<MyEntity> specifiedMyEntities = new ArrayList<>();
    private ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_COUNT);
    private ConcurrentLinkedQueue<CrawlerTask> queue = new ConcurrentLinkedQueue();
    private CommandOption commandOption;

    public TaskController(CommandOption commandOption) {
        this.commandOption = commandOption;
        if (commandOption.getSubPackage() == null) {
            commandOption.setSubPackage(false);
        }
        queue.add(new CrawlerTask(CrawlerType.DOCUMENT, ParserType.PACKAGES, commandOption));
    }

    public void execute() {
        logger.info("Starting fetch and parse...");
        String specifiedClass = this.commandOption.getSpecifiedClass();
        Map<String, MyEntity> entityMap = null;
        if (specifiedClass != null && !specifiedClass.isEmpty()) {
            entityMap = new HashMap<>();
        }
        while (!queue.isEmpty()) {
            CrawlerTask task = queue.poll();
            Future<List<MyEntity>> future = this.pool.submit(task);
            List<MyEntity> resultEntities = getResultFromFuture(future, task.getCommandOption().getUrl());
            if (ParserType.PACKAGES.equals(task.getParserType())) {
                for (MyEntity myEntity : resultEntities) {
                    queue.add(new CrawlerTask(CrawlerType.DOCUMENT, ParserType.CLASSES, new CommandOption(myEntity.getUrl(), commandOption.getTopPackageName(), myEntity.getPackageName())));
                }
            }
            if (ParserType.CLASSES.equals(task.getParserType())) {
                for (MyEntity myEntity : resultEntities) {
                    queue.add(new CrawlerTask(CrawlerType.DOCUMENT, ParserType.DETAILS, new CommandOption(myEntity.getUrl(), commandOption.getTopPackageName(), myEntity.getPackageName())));
                }
            }
            if (ParserType.DETAILS.equals(task.getParserType())) {
                this.myEntities.addAll(resultEntities);
                if (specifiedClass != null && !specifiedClass.isEmpty()) {
                    MyEntity myEntity = resultEntities.get(0);
                    entityMap.put(GenericUtil.removeGeneric(myEntity.getClassName()), myEntity);
                }
            }
        }
        this.pool.shutdown();
        logger.info("Parsed {} classes", this.myEntities.size());
        if (specifiedClass != null && !specifiedClass.isEmpty()) {
            logger.debug("Entity map size is {}", entityMap.size());
            setSpecifiedMyEntityListByMap(entityMap, specifiedClass);
            logger.info("Your specified {} classes", this.specifiedMyEntities.size());
        }
        addOtherPackageInterfaces(myEntities);
        if (specifiedClass != null) {
            addOtherPackageInterfaces(specifiedMyEntities);
        }
    }

    private void addOtherPackageInterfaces(List<MyEntity> myEntities) {
        Set<MyEntity> toAddMyEntities = new HashSet<>();
        if (myEntities != null) {
            Set<String> classNames = myEntities.stream().map(myEntity1 -> myEntity1.getClassNameWithoutGeneric()).collect(Collectors.toSet());
            for (MyEntity myEntity : myEntities) {
                List<MyEntity> interfaces = myEntity.getParentInterfaces();
                if (interfaces != null) {
                    for (MyEntity myInterface : interfaces) {
                        if (!classNames.contains(myInterface.getClassNameWithoutGeneric())) {
                            MyEntity toAddMyEntity = new MyEntity();
                            toAddMyEntity.setClassName(myInterface.getClassName());
                            toAddMyEntity.setClassNameWithoutGeneric(myInterface.getClassNameWithoutGeneric());
                            toAddMyEntity.setType(EntityType.INTERFACE);
                            toAddMyEntity.setIsAbstract(false);
                            toAddMyEntity.setPackageName(myInterface.getPackageName());
                            toAddMyEntities.add(toAddMyEntity);
                        }
                    }
                }
            }
            for (MyEntity myEntity : toAddMyEntities) {
                myEntities.add(myEntity);
            }
        }
    }

    private void setSpecifiedMyEntityListByMap(Map<String, MyEntity> entityMap, String specifiedClass) {
        Set<MyEntity> myEntitySet = new HashSet<>();
        Queue<MyEntity> myEntityQueue = new LinkedList<>();
        MyEntity specifiedEntity = entityMap.get(GenericUtil.removeGeneric(specifiedClass));
        if (specifiedEntity == null) {
            logger.debug("specified class is null");
        }
        putAllSuperClassesToSet(entityMap, specifiedEntity, myEntitySet, myEntityQueue);
        myEntitySet.remove(specifiedEntity);
        putAllSubClassesToSet(entityMap, specifiedEntity, myEntitySet, myEntityQueue);
        this.specifiedMyEntities.addAll(myEntitySet);
    }

    private void putAllSuperClassesToSet(Map<String, MyEntity> entityMap, MyEntity specifiedEntity, Set<MyEntity> myEntitySet, Queue<MyEntity> myEntityQueue) {
        addEntityToSetAndQueue(specifiedEntity, myEntitySet, myEntityQueue);
        logger.debug("Put all super class to set...");
        logger.debug("Queue size is {}", myEntityQueue.size());
        while (myEntityQueue.peek() != null) {
            MyEntity myEntity = myEntityQueue.poll();
            logger.debug("super class: {}", myEntity.getClassName());
            if (myEntity.getParentClass() != null && entityMap.get(myEntity.getParentClass().getClassNameWithoutGeneric()) != null) {
                addEntityToSetAndQueue(entityMap.get(myEntity.getParentClass().getClassNameWithoutGeneric()), myEntitySet, myEntityQueue);
            }
            List<MyEntity> superInterfaces = myEntity.getParentInterfaces();
            if (superInterfaces != null) {
                for (MyEntity e : superInterfaces) {
                    if (entityMap.get(e.getClassNameWithoutGeneric()) != null) {
                        addEntityToSetAndQueue(entityMap.get(e.getClassNameWithoutGeneric()), myEntitySet, myEntityQueue);
                    }
                }
            }
        }
    }

    private void putAllSubClassesToSet(Map<String, MyEntity> entityMap, MyEntity specifiedEntity, Set<MyEntity> myEntitySet, Queue<MyEntity> myEntityQueue) {
        addEntityToSetAndQueue(specifiedEntity, myEntitySet, myEntityQueue);
        logger.debug("Put all sub class to set...");
        logger.debug("Queue size is {}", myEntityQueue.size());
        while (myEntityQueue.peek() != null) {
            MyEntity myEntity = myEntityQueue.poll();
            logger.debug("sub class: {}", myEntity.getClassName());
            List<MyEntity> subClasses = myEntity.getSubClasses();
            if (myEntity.getSubClasses() != null) {
                for (MyEntity e : subClasses) {
                    if (entityMap.get(e.getClassNameWithoutGeneric()) != null) {
                        addEntityToSetAndQueue(entityMap.get(e.getClassNameWithoutGeneric()), myEntitySet, myEntityQueue);
                    }
                }
            }
            List<MyEntity> subInterfaces = myEntity.getSubInterfaces();
            if (myEntity.getSubInterfaces() != null) {
                for (MyEntity e : subInterfaces) {
                    if (entityMap.get(e.getClassNameWithoutGeneric()) != null) {
                        addEntityToSetAndQueue(entityMap.get(e.getClassNameWithoutGeneric()), myEntitySet, myEntityQueue);
                    }
                }
            }
        }
    }

    private void addEntityToSetAndQueue(MyEntity myEntity, Set<MyEntity> myEntitySet, Queue<MyEntity> myEntityQueue) {
        if (myEntity != null && myEntitySet != null && myEntityQueue != null) {
            if (!myEntitySet.contains(myEntity)) {
                myEntityQueue.add(myEntity);
            }
            myEntitySet.add(myEntity);
        } else {
            logger.debug("myEntity: {}, MyEntitySet: {}, MyEntityQueue: {}", myEntity, myEntitySet, myEntityQueue);
        }
    }

    private List<MyEntity> getResultFromFuture(Future<List<MyEntity>> future, String url) {
        List<MyEntity> resultEntities;
        try {
            resultEntities = future.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: {}", e.getClass().getName(), e.getMessage(), e);
            this.pool.shutdown();
            throw new TaskExecuteException(e.getMessage() + "\r\nPlease check the URL: " + url);
        }
        return resultEntities;
    }

    public List<MyEntity> getMyEntities() {
        return this.myEntities;
    }

    public List<MyEntity> getSpecifiedMyEntities() {
        return this.specifiedMyEntities;
    }
}
