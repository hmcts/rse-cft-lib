CREATE TYPE public.securityclassification AS ENUM (
    'PUBLIC',
    'PRIVATE',
    'RESTRICTED'
);

CREATE TABLE public.case_data (
                                reference bigint primary key,
                                created_date timestamp without time zone DEFAULT now() NOT NULL,
                                last_modified timestamp without time zone,
                                jurisdiction character varying(255) NOT NULL,
                                case_type_id character varying(255) NOT NULL,
                                state character varying(255) NOT NULL,
                                data jsonb NOT NULL,
                                security_classification public.securityclassification NOT NULL,
                                version integer not null DEFAULT 1,
                                last_state_modified_date timestamp without time zone,
                                supplementary_data jsonb,
                                resolved_ttl date
);


CREATE TABLE public.case_event (
                                 id bigserial primary key,
                                 created_date timestamp without time zone DEFAULT now() NOT NULL,
                                 event_id character varying(70) NOT NULL,
                                 summary character varying(1024),
                                 description character varying(65536),
                                 user_id character varying(64) NOT NULL,
                                 case_reference bigint NOT NULL references case_data(reference),
                                 case_type_id character varying(255) NOT NULL,
                                 case_type_version integer NOT NULL,
                                 state_id character varying(255) NOT NULL,
                                 data jsonb NOT NULL,
                                 user_first_name character varying(255) DEFAULT NULL::character varying NOT NULL,
                                 user_last_name character varying(255) DEFAULT NULL::character varying NOT NULL,
                                 event_name character varying(30) DEFAULT NULL::character varying NOT NULL,
                                 state_name character varying(255) DEFAULT ''::character varying NOT NULL,
                                 security_classification public.securityclassification NOT NULL,
                                 proxied_by character varying(64),
                                 proxied_by_first_name character varying(255),
                                 proxied_by_last_name character varying(255)
);


